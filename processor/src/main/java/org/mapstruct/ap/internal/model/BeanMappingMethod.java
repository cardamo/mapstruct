/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.internal.model;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;

import org.mapstruct.ap.internal.model.PropertyMapping.ConstantMappingBuilder;
import org.mapstruct.ap.internal.model.PropertyMapping.JavaExpressionMappingBuilder;
import org.mapstruct.ap.internal.model.PropertyMapping.PropertyMappingBuilder;
import org.mapstruct.ap.internal.model.common.BuilderType;
import org.mapstruct.ap.internal.model.common.Parameter;
import org.mapstruct.ap.internal.model.common.Type;
import org.mapstruct.ap.internal.model.dependency.GraphAnalyzer;
import org.mapstruct.ap.internal.model.dependency.GraphAnalyzer.GraphAnalyzerBuilder;
import org.mapstruct.ap.internal.model.source.BeanMapping;
import org.mapstruct.ap.internal.model.source.ForgedMethod;
import org.mapstruct.ap.internal.model.source.ForgedMethodHistory;
import org.mapstruct.ap.internal.model.source.Mapping;
import org.mapstruct.ap.internal.model.source.MappingOptions;
import org.mapstruct.ap.internal.model.source.Method;
import org.mapstruct.ap.internal.model.source.PropertyEntry;
import org.mapstruct.ap.internal.model.source.SelectionParameters;
import org.mapstruct.ap.internal.model.source.SourceMethod;
import org.mapstruct.ap.internal.model.source.SourceReference;
import org.mapstruct.ap.internal.model.source.TargetReference;
import org.mapstruct.ap.internal.prism.BeanMappingPrism;
import org.mapstruct.ap.internal.prism.CollectionMappingStrategyPrism;
import org.mapstruct.ap.internal.prism.NullValueMappingStrategyPrism;
import org.mapstruct.ap.internal.prism.ReportingPolicyPrism;
import org.mapstruct.ap.internal.util.MapperConfiguration;
import org.mapstruct.ap.internal.util.Message;
import org.mapstruct.ap.internal.util.Strings;
import org.mapstruct.ap.internal.util.accessor.Accessor;

import static org.mapstruct.ap.internal.model.source.Mapping.getMappingByTargetName;
import static org.mapstruct.ap.internal.util.Collections.first;
import static org.mapstruct.ap.internal.util.Message.BEANMAPPING_ABSTRACT;
import static org.mapstruct.ap.internal.util.Message.BEANMAPPING_NOT_ASSIGNABLE;
import static org.mapstruct.ap.internal.util.Message.GENERAL_ABSTRACT_RETURN_TYPE;

/**
 * A {@link MappingMethod} implemented by a {@link Mapper} class which maps one bean type to another, optionally
 * configured by one or more {@link PropertyMapping}s.
 *
 * @author Gunnar Morling
 */
public class BeanMappingMethod extends NormalTypeMappingMethod {

    private final List<PropertyMapping> propertyMappings;
    private final Map<String, List<PropertyMapping>> mappingsByParameter;
    private final List<PropertyMapping> constantMappings;
    private final Type returnTypeToConstruct;
    private final BuilderType returnTypeBuilder;
    private final MethodReference finalizerMethod;

    public static class Builder {

        private MappingBuilderContext ctx;
        private Method method;

        /* returnType to construct can have a builder */
        private BuilderType returnTypeBuilder;

        private Map<String, Accessor> unprocessedTargetProperties;
        private Map<String, Accessor> unprocessedSourceProperties;
        private Set<String> targetProperties;
        private final List<PropertyMapping> propertyMappings = new ArrayList<>();
        private final Set<Parameter> unprocessedSourceParameters = new HashSet<>();
        private final Set<String> existingVariableNames = new HashSet<>();
        private Function<String, Mapping> singleMapping;
        private Consumer<Set<Mapping>> mappingsInitializer;
        private final Map<String, Set<Mapping>> unprocessedDefinedTargets = new LinkedHashMap<>();

        public Builder mappingContext(MappingBuilderContext mappingContext) {
            this.ctx = mappingContext;
            return this;
        }

        public Builder returnTypeBuilder( BuilderType returnTypeBuilder ) {
            this.returnTypeBuilder = returnTypeBuilder;
            return this;
        }

        public Builder sourceMethod(SourceMethod sourceMethod) {
            singleMapping =
                targetName -> getMappingByTargetName( targetName, sourceMethod.getMappingOptions().getMappings() );
            mappingsInitializer =
                mappings -> mappings.stream().forEach( mapping -> initReferencesForSourceMethodMapping( mapping ) );
            this.method = sourceMethod;
            return this;
        }

        public Builder forgedMethod(Method method) {
            singleMapping = targetPropertyName -> null;
            mappingsInitializer =
                mappings -> mappings.stream().forEach( mapping -> initReferencesForForgedMethodMapping( mapping ) );
            this.method = method;
            return this;
        }

        public BeanMappingMethod build() {

            BeanMapping beanMapping = method.getMappingOptions().getBeanMapping();
            SelectionParameters selectionParameters = beanMapping != null ? beanMapping.getSelectionParameters() : null;

            /* the return type that needs to be constructed (new or factorized), so for instance: */
            /*  1) the return type of a non-update method */
            /*  2) or the implementation type that needs to be used when the return type is abstract */
            /*  3) or the builder whenever the return type is immutable */
            Type returnTypeToConstruct = null;

            /* factory or builder method to construct the returnTypeToConstruct */
            MethodReference factoryMethod = null;

            // determine which return type to construct
            if ( !method.getReturnType().isVoid() ) {
                Type returnTypeImpl = getReturnTypeToConstructFromSelectionParameters( selectionParameters );
                if ( returnTypeImpl != null ) {
                    factoryMethod = getFactoryMethod( returnTypeImpl, selectionParameters );
                    if ( factoryMethod != null || canBeConstructed( returnTypeImpl ) ) {
                        returnTypeToConstruct = returnTypeImpl;
                    }
                    else {
                        reportResultTypeFromBeanMappingNotConstructableError( returnTypeImpl );
                    }
                }
                else {
                    returnTypeImpl = isBuilderRequired() ? returnTypeBuilder.getBuilder() : method.getReturnType();
                    factoryMethod = getFactoryMethod( returnTypeImpl, selectionParameters );
                    if ( factoryMethod != null || canBeConstructed( returnTypeImpl ) ) {
                        returnTypeToConstruct = returnTypeImpl;
                    }
                    else {
                        reportReturnTypeNotConstructableError( returnTypeImpl );
                    }
                }
            }

            /* the type that needs to be used in the mapping process as target */
            Type resultTypeToMap = returnTypeToConstruct == null ? method.getResultType() : returnTypeToConstruct;

            /* initialize mappings for this method && filter invalid inverse methods */
            mappingsInitializer.accept( method.getMappingOptions().getMappings() );
            method.getMappingOptions().getMappings().removeIf( mapping -> !isValidWhenReversed( mapping ) );

            CollectionMappingStrategyPrism cms = this.method.getMapperConfiguration().getCollectionMappingStrategy();

            // determine accessors
            Map<String, Accessor> accessors = resultTypeToMap.getPropertyWriteAccessors( cms );
            this.targetProperties = accessors.keySet();

            this.unprocessedTargetProperties = new LinkedHashMap<>( accessors );
            this.unprocessedSourceProperties = new LinkedHashMap<>();
            for ( Parameter sourceParameter : method.getSourceParameters() ) {
                unprocessedSourceParameters.add( sourceParameter );

                if ( sourceParameter.getType().isPrimitive() || sourceParameter.getType().isArrayType() ) {
                    continue;
                }
                Map<String, Accessor> readAccessors = sourceParameter.getType().getPropertyReadAccessors();
                for ( String key : readAccessors.keySet() ) {
                    unprocessedSourceProperties.put( key, readAccessors.get( key ) );
                }
            }
            existingVariableNames.addAll( method.getParameterNames() );

            // get bean mapping (when specified as annotation )
            if ( beanMapping != null ) {
                for ( String ignoreUnmapped : beanMapping.getIgnoreUnmappedSourceProperties() ) {
                    unprocessedSourceProperties.remove( ignoreUnmapped );
                }
            }

            // map properties with mapping
            boolean mappingErrorOccured = handleDefinedMappings();
            if ( mappingErrorOccured ) {
                return null;
            }

            if ( !method.getMappingOptions().isRestrictToDefinedMappings() ) {

                // map properties without a mapping
                applyPropertyNameBasedMapping();

                // map parameters without a mapping
                applyParameterNameBasedMapping();
            }

            // Process the unprocessed defined targets
            handleUnprocessedDefinedTargets();

            // report errors on unmapped properties
            reportErrorForUnmappedTargetPropertiesIfRequired();
            reportErrorForUnmappedSourcePropertiesIfRequired();

            // mapNullToDefault
            NullValueMappingStrategyPrism nullValueMappingStrategy =
                beanMapping != null ? beanMapping.getNullValueMappingStrategy() : null;
            boolean mapNullToDefault = method.getMapperConfiguration().isMapToDefault( nullValueMappingStrategy );

            // sort
            sortPropertyMappingsByDependencies();

            // before / after mappings
            List<LifecycleCallbackMethodReference> beforeMappingMethods = LifecycleMethodResolver.beforeMappingMethods(
                            method,
                            resultTypeToMap,
                            selectionParameters,
                            ctx,
                            existingVariableNames
            );
            List<LifecycleCallbackMethodReference> afterMappingMethods = LifecycleMethodResolver.afterMappingMethods(
                            method,
                            resultTypeToMap,
                            selectionParameters,
                            ctx,
                            existingVariableNames
            );

            if (factoryMethod != null && method instanceof ForgedMethod ) {
                ( (ForgedMethod) method ).addThrownTypes( factoryMethod.getThrownTypes() );
            }

            MethodReference finalizeMethod = null;

            if ( shouldCallFinalizerMethod( returnTypeToConstruct ) ) {
                finalizeMethod = getFinalizerMethod();
            }

            return new BeanMappingMethod(
                method,
                existingVariableNames,
                propertyMappings,
                factoryMethod,
                mapNullToDefault,
                returnTypeToConstruct,
                returnTypeBuilder,
                beforeMappingMethods,
                afterMappingMethods,
                finalizeMethod
            );
        }

        /**
         * @return builder is required when there is a returnTypeBuilder and the mapping method is not update method.
         * However, builder is also required when there is a returnTypeBuilder, the mapping target is the builder and
         * builder is not assignable to the return type (so without building).
         */
        private boolean isBuilderRequired() {
            return returnTypeBuilder != null
                    && ( !method.isUpdateMethod() || !method.isMappingTargetAssignableToReturnType() );
        }

        private boolean shouldCallFinalizerMethod(Type returnTypeToConstruct ) {
            if ( returnTypeToConstruct == null ) {
                return false;
            }
            else if ( returnTypeToConstruct.isAssignableTo( method.getReturnType() ) ) {
                // If the mapping type can be assigned to the return type then we
                // don't need a finalizer method
                return false;
            }

            return returnTypeBuilder != null;
        }

        private MethodReference getFinalizerMethod() {
            return BuilderFinisherMethodResolver.getBuilderFinisherMethod(
                method,
                returnTypeBuilder,
                ctx
            );
        }

        /**
         * If there were nested defined targets that have not been handled. Then we need to process them at the end.
         */
        private void handleUnprocessedDefinedTargets() {
            Iterator<Entry<String, Set<Mapping>>> iterator = unprocessedDefinedTargets.entrySet().iterator();

            // For each of the unprocessed defined targets forge a mapping for each of the
            // method source parameters. The generated mappings are not going to use forged name based mappings.
            while ( iterator.hasNext() ) {
                Entry<String, Set<Mapping>> entry = iterator.next();
                String propertyName = entry.getKey();
                if ( !unprocessedTargetProperties.containsKey( propertyName ) ) {
                    continue;
                }
                List<Parameter> sourceParameters = method.getSourceParameters();
                boolean forceUpdateMethod = sourceParameters.size() > 1;
                for ( Parameter sourceParameter : sourceParameters ) {
                    SourceReference reference = new SourceReference.BuilderFromProperty()
                        .sourceParameter( sourceParameter )
                        .name( propertyName )
                        .build();

                    MappingOptions mappingOptions = extractAdditionalOptions( propertyName, true );
                    PropertyMapping propertyMapping = new PropertyMappingBuilder()
                        .mappingContext( ctx )
                        .sourceMethod( method )
                        .targetWriteAccessor( unprocessedTargetProperties.get( propertyName ) )
                        .targetReadAccessor( getTargetPropertyReadAccessor( propertyName ) )
                        .targetPropertyName( propertyName )
                        .sourceReference( reference )
                        .existingVariableNames( existingVariableNames )
                        .dependsOn( mappingOptions.collectNestedDependsOn() )
                        .forgeMethodWithMappingOptions( mappingOptions )
                        .forceUpdateMethod( forceUpdateMethod )
                        .forgedNamedBased( false )
                        .build();

                    if ( propertyMapping != null ) {
                        unprocessedTargetProperties.remove( propertyName );
                        unprocessedSourceProperties.remove( propertyName );
                        iterator.remove();
                        propertyMappings.add( propertyMapping );
                        // If we found a mapping for the unprocessed property then stop
                        break;
                    }
                }
            }
        }

        /**
         * Sources the given mappings as per the dependency relationships given via {@code dependsOn()}. If a cycle is
         * detected, an error is reported.
         */
        private void sortPropertyMappingsByDependencies() {
            GraphAnalyzerBuilder graphAnalyzerBuilder = GraphAnalyzer.builder();

            for ( PropertyMapping propertyMapping : propertyMappings ) {
                graphAnalyzerBuilder.withNode( propertyMapping.getName(), propertyMapping.getDependsOn() );
            }

            final GraphAnalyzer graphAnalyzer = graphAnalyzerBuilder.build();

            if ( !graphAnalyzer.getCycles().isEmpty() ) {
                Set<String> cycles = new HashSet<>();
                for ( List<String> cycle : graphAnalyzer.getCycles() ) {
                    cycles.add( Strings.join( cycle, " -> " ) );
                }

                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    Message.BEANMAPPING_CYCLE_BETWEEN_PROPERTIES, Strings.join( cycles, ", " )
                );
            }
            else {
                Collections.sort(
                    propertyMappings, new Comparator<PropertyMapping>() {
                        @Override
                        public int compare(PropertyMapping o1, PropertyMapping o2) {
                            return graphAnalyzer.getTraversalSequence( o1.getName() )
                                - graphAnalyzer.getTraversalSequence( o2.getName() );
                        }
                    }
                );
            }
        }

        private Type getReturnTypeToConstructFromSelectionParameters(SelectionParameters selectionParams) {
            if ( selectionParams != null && selectionParams.getResultType() != null ) {
                return ctx.getTypeFactory().getType( selectionParams.getResultType() );
            }
            return null;
        }

        private boolean canBeConstructed(Type typeToBeConstructed) {
            return !typeToBeConstructed.isAbstract()
                    && typeToBeConstructed.isAssignableTo( this.method.getResultType() )
                    && typeToBeConstructed.hasEmptyAccessibleContructor();
        }

        private void reportResultTypeFromBeanMappingNotConstructableError(Type resultType) {

            if ( resultType.isAbstract() ) {
                ctx.getMessager().printMessage(
                                method.getExecutable(),
                                BeanMappingPrism.getInstanceOn( method.getExecutable() ).mirror,
                                BEANMAPPING_ABSTRACT,
                                resultType,
                                method.getResultType()
                );
            }
            else if ( !resultType.isAssignableTo( method.getResultType() ) ) {
                ctx.getMessager().printMessage(
                                method.getExecutable(),
                                BeanMappingPrism.getInstanceOn( method.getExecutable() ).mirror,
                                BEANMAPPING_NOT_ASSIGNABLE,
                                resultType,
                                method.getResultType()
                );
            }
            else if ( !resultType.hasEmptyAccessibleContructor() ) {
                ctx.getMessager().printMessage(
                                method.getExecutable(),
                                BeanMappingPrism.getInstanceOn( method.getExecutable() ).mirror,
                                Message.GENERAL_NO_SUITABLE_CONSTRUCTOR,
                                resultType
                );
            }
        }

        private void reportReturnTypeNotConstructableError(Type returnType) {
            if ( returnType.isAbstract() ) {
                ctx.getMessager().printMessage(
                                method.getExecutable(),
                                GENERAL_ABSTRACT_RETURN_TYPE,
                                returnType
                );
            }
            else if ( !returnType.hasEmptyAccessibleContructor() ) {
                ctx.getMessager().printMessage(
                                method.getExecutable(),
                                Message.GENERAL_NO_SUITABLE_CONSTRUCTOR,
                                returnType
                );
            }
        }

        /**
         * Find a factory method for a return type or for a builder.
         * @param returnTypeImpl the return type implementation to construct
         * @param selectionParameters
         * @return
         */
        private MethodReference getFactoryMethod(Type returnTypeImpl, SelectionParameters selectionParameters) {
            MethodReference factoryMethod = ObjectFactoryMethodResolver.getFactoryMethod( method,
                            returnTypeImpl,
                            selectionParameters,
                            ctx
            );
            if ( factoryMethod == null && returnTypeBuilder != null ) {
                factoryMethod = ObjectFactoryMethodResolver.getBuilderFactoryMethod( method, returnTypeBuilder );
            }

            return factoryMethod;
        }

        /**
         * Initialized the source- and target reference for a certain mapping for regular non forged methods
         *
         * @param mapping the mapping
         */
        private void initReferencesForSourceMethodMapping(Mapping mapping ) {

            // handle source reference
            SourceReference sourceReference = new SourceReference.BuilderFromMapping()
                .mapping( mapping )
                .method( method )
                .messager( ctx.getMessager() )
                .typeFactory( ctx.getTypeFactory() )
                .build();
            mapping.setSourceReference( sourceReference );

            // handle target reference
            TargetReference targetReference = new TargetReference.BuilderFromTargetMapping()
                .mapping( mapping )
                .method( method )
                .messager( ctx.getMessager() )
                .typeFactory( ctx.getTypeFactory() )
                .build();
            mapping.setTargetReference( targetReference );

        }

        /**
         * Initialized the source- and target reference for forged methods (e.g. when handling nesting)
         *
         * @param mapping the mapping
         */
        private void initReferencesForForgedMethodMapping(Mapping mapping ) {

            /* a forge method has always one mandatory source parameter and no more */
            Parameter sourceParameter = first( Parameter.getSourceParameters( method.getParameters() ) );

            SourceReference sourceReference = mapping.getSourceReference();
            if ( sourceReference != null ) {
                SourceReference oldSourceReference = sourceReference;
                sourceReference = new SourceReference.BuilderFromSourceReference()
                    .sourceParameter( sourceParameter )
                    .sourceReference( oldSourceReference )
                    .build();
            }
            mapping.setSourceReference( sourceReference );

        }

        /**
         * MapStruct filters automatically inversed invalid methods out. TODO: this is a principle we should discuss!
         * @param mapping
         * @return
         */
        private static boolean isValidWhenReversed(Mapping mapping) {
            if ( mapping.getInheritContext() != null && mapping.getInheritContext().isReversed() ) {
                return mapping.getTargetReference().isValid() && ( mapping.getSourceReference() != null ?
                    mapping.getSourceReference().isValid() :
                    true );
            }
            return true;
        }

        /**
         * Iterates over all defined mapping methods ({@code @Mapping(s)}), either directly given or inherited from the
         * inverse mapping method.
         * <p>
         * If a match is found between a defined source (constant, expression, ignore or source) the mapping is removed
         * from the remaining target properties.
         * <p>
         * It is furthermore checked whether the given mappings are correct. When an error occurs, the method continues
         * in search of more problems.
         */
        private boolean handleDefinedMappings() {

            boolean errorOccurred = false;
            Set<String> handledTargets = new HashSet<>();

            // first we have to handle nested target mappings
            if ( method.getMappingOptions().hasNestedTargetReferences() ) {
                errorOccurred = handleDefinedNestedTargetMapping( handledTargets );
            }

            for ( Mapping mapping : method.getMappingOptions().getMappings() ) {
                TargetReference targetReference = mapping.getTargetReference();
                if ( targetReference.isValid() ) {
                    String target = first( targetReference.getPropertyEntries() ).getFullName();
                    if ( !handledTargets.contains( target ) ) {
                        if ( handleDefinedMapping( mapping, handledTargets ) ) {
                            errorOccurred = true;
                        }
                    }
                    if ( mapping.getSourceReference() != null && mapping.getSourceReference().isValid() ) {
                        List<PropertyEntry> sourceEntries = mapping.getSourceReference().getPropertyEntries();
                        if ( !sourceEntries.isEmpty() ) {
                            String source = first( sourceEntries ).getFullName();
                            unprocessedSourceProperties.remove( source );
                        }
                    }
                }
                else {
                    errorOccurred = true;
                }
            }

            // remove the remaining name based properties
            for ( String handledTarget : handledTargets ) {
                unprocessedTargetProperties.remove( handledTarget );
                unprocessedDefinedTargets.remove( handledTarget );
            }

            return errorOccurred;
        }

        private boolean handleDefinedNestedTargetMapping(Set<String> handledTargets) {

            NestedTargetPropertyMappingHolder holder = new NestedTargetPropertyMappingHolder.Builder()
                .mappingContext( ctx )
                .method( method )
                .existingVariableNames( existingVariableNames )
                .build();

            unprocessedSourceParameters.removeAll( holder.getProcessedSourceParameters() );
            propertyMappings.addAll( holder.getPropertyMappings() );
            handledTargets.addAll( holder.getHandledTargets() );
            // Store all the unprocessed defined targets.
            for ( Entry<PropertyEntry, Set<Mapping>> entry : holder.getUnprocessedDefinedTarget().entrySet() ) {
                if ( entry.getValue().isEmpty() ) {
                    continue;
                }
                unprocessedDefinedTargets.put( entry.getKey().getName(), entry.getValue() );
            }
            return holder.hasErrorOccurred();
        }

        private boolean handleDefinedMapping(Mapping mapping, Set<String> handledTargets) {

            boolean errorOccured = false;

            PropertyMapping propertyMapping = null;

            TargetReference targetRef = mapping.getTargetReference();
            PropertyEntry targetProperty = first( targetRef.getPropertyEntries() );
            String propertyName = targetProperty.getName();

            // unknown properties given via dependsOn()?
            for ( String dependency : mapping.getDependsOn() ) {
                if ( !targetProperties.contains( dependency ) ) {
                    ctx.getMessager().printMessage(
                        method.getExecutable(),
                        mapping.getMirror(),
                        mapping.getDependsOnAnnotationValue(),
                        Message.BEANMAPPING_UNKNOWN_PROPERTY_IN_DEPENDS_ON,
                        dependency
                    );
                    errorOccured = true;
                }
            }

            // check the mapping options
            // its an ignored property mapping
            if ( mapping.isIgnored() ) {
                propertyMapping = null;
                handledTargets.add( mapping.getTargetName() );
            }

            // its a plain-old property mapping
            else if ( mapping.getSourceName() != null ) {

                // determine source parameter
                SourceReference sourceRef = mapping.getSourceReference();
                if ( sourceRef.isValid() ) {

                    // targetProperty == null can occur: we arrived here because we want as many errors
                    // as possible before we stop analysing
                    propertyMapping = new PropertyMappingBuilder()
                        .mappingContext( ctx )
                        .sourceMethod( method )
                        .targetProperty( targetProperty )
                        .targetPropertyName( mapping.getTargetName() )
                        .sourcePropertyName( mapping.getSourceName() )
                        .sourceReference( sourceRef )
                        .selectionParameters( mapping.getSelectionParameters() )
                        .formattingParameters( mapping.getFormattingParameters() )
                        .existingVariableNames( existingVariableNames )
                        .dependsOn( mapping.getDependsOn() )
                        .defaultValue( mapping.getDefaultValue() )
                        .defaultJavaExpression( mapping.getDefaultJavaExpression() )
                        .mirror( mapping.getMirror() )
                        .nullValueCheckStrategy( mapping.getNullValueCheckStrategy() )
                        .nullValuePropertyMappingStrategy( mapping.getNullValuePropertyMappingStrategy() )
                        .build();
                    handledTargets.add( propertyName );
                    unprocessedSourceParameters.remove( sourceRef.getParameter() );
                }
                else {
                    errorOccured = true;
                }
            }

            // its a constant
            // if we have an unprocessed target that means that it most probably is nested and we should
            // not generated any mapping for it now. Eventually it will be done though
            else if ( mapping.getConstant() != null && !unprocessedDefinedTargets.containsKey( propertyName ) ) {

                propertyMapping = new ConstantMappingBuilder()
                    .mappingContext( ctx )
                    .sourceMethod( method )
                    .constantExpression( mapping.getConstant() )
                    .targetProperty( targetProperty )
                    .targetPropertyName( mapping.getTargetName() )
                    .formattingParameters( mapping.getFormattingParameters() )
                    .selectionParameters( mapping.getSelectionParameters() )
                    .existingVariableNames( existingVariableNames )
                    .dependsOn( mapping.getDependsOn() )
                    .mirror( mapping.getMirror() )
                    .build();
                handledTargets.add( mapping.getTargetName() );
            }

            // its an expression
            // if we have an unprocessed target that means that it most probably is nested and we should
            // not generated any mapping for it now. Eventually it will be done though
            else if ( mapping.getJavaExpression() != null && !unprocessedDefinedTargets.containsKey( propertyName ) ) {

                propertyMapping = new JavaExpressionMappingBuilder()
                    .mappingContext( ctx )
                    .sourceMethod( method )
                    .javaExpression( mapping.getJavaExpression() )
                    .existingVariableNames( existingVariableNames )
                    .targetProperty( targetProperty )
                    .targetPropertyName( mapping.getTargetName() )
                    .dependsOn( mapping.getDependsOn() )
                    .mirror( mapping.getMirror() )
                    .build();
                handledTargets.add( mapping.getTargetName() );
            }

            // remaining are the mappings without a 'source' so, 'only' a date format or qualifiers
            if ( propertyMapping != null ) {
                propertyMappings.add( propertyMapping );
            }

            return errorOccured;
        }

        /**
         * Iterates over all target properties and all source parameters.
         * <p>
         * When a property name match occurs, the remainder will be checked for duplicates. Matches will be removed from
         * the set of remaining target properties.
         */
        private void applyPropertyNameBasedMapping() {

            Iterator<Entry<String, Accessor>> targetPropertyEntriesIterator =
                unprocessedTargetProperties.entrySet().iterator();

            while ( targetPropertyEntriesIterator.hasNext() ) {

                Entry<String, Accessor> targetProperty = targetPropertyEntriesIterator.next();
                String targetPropertyName = targetProperty.getKey();

                PropertyMapping propertyMapping = null;

                if ( propertyMapping == null ) {

                    for ( Parameter sourceParameter : method.getSourceParameters() ) {

                        Type sourceType = sourceParameter.getType();

                        if ( sourceType.isPrimitive() || sourceType.isArrayType() ) {
                            continue;
                        }

                        PropertyMapping newPropertyMapping = null;

                        Accessor sourceReadAccessor =
                            sourceParameter.getType().getPropertyReadAccessors().get( targetPropertyName );

                        Accessor sourcePresenceChecker =
                            sourceParameter.getType().getPropertyPresenceCheckers().get( targetPropertyName );

                        if ( sourceReadAccessor != null ) {
                            Mapping mapping = singleMapping.apply( targetProperty.getKey() );
                            DeclaredType declaredSourceType = (DeclaredType) sourceParameter.getType().getTypeMirror();

                            SourceReference sourceRef = new SourceReference.BuilderFromProperty()
                                .sourceParameter( sourceParameter )
                                .type( ctx.getTypeFactory().getReturnType( declaredSourceType, sourceReadAccessor ) )
                                .readAccessor( sourceReadAccessor )
                                .presenceChecker( sourcePresenceChecker )
                                .name( targetProperty.getKey() )
                                .build();

                            newPropertyMapping = new PropertyMappingBuilder()
                                .mappingContext( ctx )
                                .sourceMethod( method )
                                .targetWriteAccessor( targetProperty.getValue() )
                                .targetReadAccessor( getTargetPropertyReadAccessor( targetPropertyName ) )
                                .targetPropertyName( targetPropertyName )
                                .sourceReference( sourceRef )
                                .formattingParameters( mapping != null ? mapping.getFormattingParameters() : null )
                                .selectionParameters( mapping != null ? mapping.getSelectionParameters() : null )
                                .defaultValue( mapping != null ? mapping.getDefaultValue() : null )
                                .existingVariableNames( existingVariableNames )
                                .dependsOn( mapping != null ? mapping.getDependsOn() : Collections.<String>emptySet() )
                                .forgeMethodWithMappingOptions( extractAdditionalOptions( targetPropertyName, false ) )
                                .nullValueCheckStrategy( mapping != null ? mapping.getNullValueCheckStrategy() : null )
                                .nullValuePropertyMappingStrategy( mapping != null ?
                                    mapping.getNullValuePropertyMappingStrategy() : null )
                                .mirror( mapping != null ? mapping.getMirror() : null )
                                .build();

                            unprocessedSourceParameters.remove( sourceParameter );
                        }

                        if ( propertyMapping != null && newPropertyMapping != null ) {
                            // TODO improve error message
                            ctx.getMessager().printMessage(
                                method.getExecutable(),
                                Message.BEANMAPPING_SEVERAL_POSSIBLE_SOURCES,
                                targetPropertyName
                            );
                            break;
                        }
                        else if ( newPropertyMapping != null ) {
                            propertyMapping = newPropertyMapping;
                        }
                    }
                }

                if ( propertyMapping != null ) {
                    propertyMappings.add( propertyMapping );
                    targetPropertyEntriesIterator.remove();
                    unprocessedDefinedTargets.remove( targetPropertyName );
                    unprocessedSourceProperties.remove( targetPropertyName );
                }
            }
        }

        private void applyParameterNameBasedMapping() {

            Iterator<Entry<String, Accessor>> targetPropertyEntriesIterator =
                unprocessedTargetProperties.entrySet().iterator();

            while ( targetPropertyEntriesIterator.hasNext() ) {

                Entry<String, Accessor> targetProperty = targetPropertyEntriesIterator.next();

                Iterator<Parameter> sourceParameters = unprocessedSourceParameters.iterator();

                while ( sourceParameters.hasNext() ) {

                    Parameter sourceParameter = sourceParameters.next();
                    if ( sourceParameter.getName().equals( targetProperty.getKey() ) ) {
                        Mapping mapping = singleMapping.apply( targetProperty.getKey() );

                        SourceReference sourceRef = new SourceReference.BuilderFromProperty()
                            .sourceParameter( sourceParameter )
                            .name( targetProperty.getKey() )
                            .build();

                        PropertyMapping propertyMapping = new PropertyMappingBuilder()
                            .mappingContext( ctx )
                            .sourceMethod( method )
                            .targetWriteAccessor( targetProperty.getValue() )
                            .targetReadAccessor( getTargetPropertyReadAccessor( targetProperty.getKey() ) )
                            .targetPropertyName( targetProperty.getKey() )
                            .sourceReference( sourceRef )
                            .formattingParameters( mapping != null ? mapping.getFormattingParameters() : null )
                            .selectionParameters( mapping != null ? mapping.getSelectionParameters() : null )
                            .existingVariableNames( existingVariableNames )
                            .dependsOn( mapping != null ? mapping.getDependsOn() : Collections.<String>emptySet() )
                            .forgeMethodWithMappingOptions( extractAdditionalOptions( targetProperty.getKey(), false ) )
                            .nullValueCheckStrategy( mapping != null ? mapping.getNullValueCheckStrategy() : null )
                            .nullValuePropertyMappingStrategy( mapping != null ?
                                mapping.getNullValuePropertyMappingStrategy() : null )
                            .mirror( mapping != null ? mapping.getMirror() : null )
                            .build();

                        propertyMappings.add( propertyMapping );
                        targetPropertyEntriesIterator.remove();
                        sourceParameters.remove();
                        unprocessedDefinedTargets.remove( targetProperty.getKey() );
                        unprocessedSourceProperties.remove( targetProperty.getKey() );
                    }
                }
            }
        }

        private MappingOptions extractAdditionalOptions(String targetProperty, boolean restrictToDefinedMappings) {
            MappingOptions additionalOptions = null;
            if ( unprocessedDefinedTargets.containsKey( targetProperty ) ) {
                Set<Mapping> mappings = unprocessedDefinedTargets.get( targetProperty );
                additionalOptions = MappingOptions.forMappingsOnly( mappings, restrictToDefinedMappings );
            }
            return additionalOptions;
        }

        private Accessor getTargetPropertyReadAccessor(String propertyName) {
            return method.getResultType().getPropertyReadAccessors().get( propertyName );
        }

        private ReportingPolicyPrism getUnmappedTargetPolicy() {
            MappingOptions mappingOptions = method.getMappingOptions();
            if ( mappingOptions.getBeanMapping() != null &&
                mappingOptions.getBeanMapping().getReportingPolicy() != null ) {
                return mappingOptions.getBeanMapping().getReportingPolicy();
            }

            MapperConfiguration mapperSettings = MapperConfiguration.getInstanceOn( ctx.getMapperTypeElement() );

            return mapperSettings.unmappedTargetPolicy( ctx.getOptions() );
        }

        private void reportErrorForUnmappedTargetPropertiesIfRequired() {

            // fetch settings from element to implement
            ReportingPolicyPrism unmappedTargetPolicy = getUnmappedTargetPolicy();

            if ( method instanceof ForgedMethod && targetProperties.isEmpty() ) {
                //TODO until we solve 1140 we report this error when the target properties are empty
                ForgedMethod forgedMethod = (ForgedMethod) method;
                if ( forgedMethod.getHistory() == null ) {
                    Type sourceType = this.method.getParameters().get( 0 ).getType();
                    Type targetType = this.method.getReturnType();
                    ctx.getMessager().printMessage(
                        this.method.getExecutable(),
                        Message.PROPERTYMAPPING_FORGED_MAPPING_NOT_FOUND,
                        sourceType,
                        targetType,
                        targetType,
                        sourceType
                    );
                }
                else {
                    ForgedMethodHistory history = forgedMethod.getHistory();
                    ctx.getMessager().printMessage(
                        this.method.getExecutable(),
                        Message.PROPERTYMAPPING_FORGED_MAPPING_WITH_HISTORY_NOT_FOUND,
                        history.createSourcePropertyErrorMessage(),
                        history.getTargetType(),
                        history.createTargetPropertyName(),
                        history.getTargetType(),
                        history.getSourceType()
                    );
                }
            }
            else if ( !unprocessedTargetProperties.isEmpty() && unmappedTargetPolicy.requiresReport() ) {

                Message msg = unmappedTargetPolicy.getDiagnosticKind() == Diagnostic.Kind.ERROR ?
                    Message.BEANMAPPING_UNMAPPED_TARGETS_ERROR : Message.BEANMAPPING_UNMAPPED_TARGETS_WARNING;
                Object[] args = new Object[] {
                    MessageFormat.format(
                        "{0,choice,1#property|1<properties}: \"{1}\"",
                        unprocessedTargetProperties.size(),
                        Strings.join( unprocessedTargetProperties.keySet(), ", " )
                    )
                };
                if ( method instanceof ForgedMethod ) {
                    msg = unmappedTargetPolicy.getDiagnosticKind() == Diagnostic.Kind.ERROR ?
                        Message.BEANMAPPING_UNMAPPED_FORGED_TARGETS_ERROR :
                        Message.BEANMAPPING_UNMAPPED_FORGED_TARGETS_WARNING;
                    String sourceErrorMessage = method.getParameters().get( 0 ).getType().toString();
                    String targetErrorMessage = method.getReturnType().toString();
                    if ( ( (ForgedMethod) method ).getHistory() != null ) {
                        ForgedMethodHistory history = ( (ForgedMethod) method ).getHistory();
                        sourceErrorMessage = history.createSourcePropertyErrorMessage();
                        targetErrorMessage = MessageFormat.format(
                            "\"{0} {1}\"",
                            history.getTargetType(),
                            history.createTargetPropertyName()
                        );
                    }
                    args = new Object[] {
                        args[0],
                        sourceErrorMessage,
                        targetErrorMessage
                    };
                }

                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    msg,
                    args
                );
            }
        }

        private ReportingPolicyPrism getUnmappedSourcePolicy() {
            MapperConfiguration mapperSettings = MapperConfiguration.getInstanceOn( ctx.getMapperTypeElement() );

            return mapperSettings.unmappedSourcePolicy();
        }

        private void reportErrorForUnmappedSourcePropertiesIfRequired() {
            ReportingPolicyPrism unmappedSourcePolicy = getUnmappedSourcePolicy();

            if ( !unprocessedSourceProperties.isEmpty() && unmappedSourcePolicy.requiresReport() ) {

                Message msg = unmappedSourcePolicy.getDiagnosticKind() == Diagnostic.Kind.ERROR ?
                    Message.BEANMAPPING_UNMAPPED_SOURCES_ERROR : Message.BEANMAPPING_UNMAPPED_SOURCES_WARNING;
                Object[] args = new Object[] {
                    MessageFormat.format(
                        "{0,choice,1#property|1<properties}: \"{1}\"",
                        unprocessedSourceProperties.size(),
                        Strings.join( unprocessedSourceProperties.keySet(), ", " )
                    )
                };

                ctx.getMessager().printMessage(
                    method.getExecutable(),
                    msg,
                    args
                );
            }
        }
    }

    private BeanMappingMethod(Method method,
                              Collection<String> existingVariableNames,
                              List<PropertyMapping> propertyMappings,
                              MethodReference factoryMethod,
                              boolean mapNullToDefault,
                              Type returnTypeToConstruct,
                              BuilderType returnTypeBuilder,
                              List<LifecycleCallbackMethodReference> beforeMappingReferences,
                              List<LifecycleCallbackMethodReference> afterMappingReferences,
                              MethodReference finalizerMethod) {
        super(
            method,
            existingVariableNames,
            factoryMethod,
            mapNullToDefault,
            beforeMappingReferences,
            afterMappingReferences
        );

        this.propertyMappings = propertyMappings;
        this.returnTypeBuilder = returnTypeBuilder;
        this.finalizerMethod = finalizerMethod;

        // intialize constant mappings as all mappings, but take out the ones that can be contributed to a
        // parameter mapping.
        this.mappingsByParameter = new HashMap<>();
        this.constantMappings = new ArrayList<>( propertyMappings );
        for ( Parameter sourceParameter : getSourceParameters() ) {
            ArrayList<PropertyMapping> mappingsOfParameter = new ArrayList<>();
            mappingsByParameter.put( sourceParameter.getName(), mappingsOfParameter );
            for ( PropertyMapping mapping : propertyMappings ) {
                if ( sourceParameter.getName().equals( mapping.getSourceBeanName() ) ) {
                    mappingsOfParameter.add( mapping );
                    constantMappings.remove( mapping );
                }
            }
        }
        this.returnTypeToConstruct = returnTypeToConstruct;
    }

    public List<PropertyMapping> getConstantMappings() {
        return constantMappings;
    }

    public List<PropertyMapping> propertyMappingsByParameter(Parameter parameter) {
        // issues: #909 and #1244. FreeMarker has problem getting values from a map when the search key is size or value
        return mappingsByParameter.get( parameter.getName() );
    }

    public Type getReturnTypeToConstruct() {
        return returnTypeToConstruct;
    }

    public MethodReference getFinalizerMethod() {
        return finalizerMethod;
    }

    @Override
    public Set<Type> getImportTypes() {
        Set<Type> types = super.getImportTypes();

        for ( PropertyMapping propertyMapping : propertyMappings ) {
            types.addAll( propertyMapping.getImportTypes() );
        }

        if ( returnTypeToConstruct != null  ) {
            types.addAll( returnTypeToConstruct.getImportTypes() );
        }
        if ( returnTypeBuilder != null ) {
            types.add( returnTypeBuilder.getOwningType() );
        }

        return types;
    }

    public List<Parameter> getSourceParametersExcludingPrimitives() {
        List<Parameter> sourceParameters = new ArrayList<>();
        for ( Parameter sourceParam : getSourceParameters() ) {
            if ( !sourceParam.getType().isPrimitive() ) {
                sourceParameters.add( sourceParam );
            }
        }

        return sourceParameters;
    }

    public List<Parameter> getSourcePrimitiveParameters() {
        List<Parameter> sourceParameters = new ArrayList<>();
        for ( Parameter sourceParam : getSourceParameters() ) {
            if ( sourceParam.getType().isPrimitive() ) {
                sourceParameters.add( sourceParam );
            }
        }
        return sourceParameters;
    }

    @Override
    public int hashCode() {
        //Needed for Checkstyle, otherwise it fails due to EqualsHashCode rule
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if ( this == obj ) {
            return true;
        }
        if ( obj == null || getClass() != obj.getClass() ) {
            return false;
        }

        BeanMappingMethod that = (BeanMappingMethod) obj;

        if ( !super.equals( obj ) ) {
            return false;
        }
        return propertyMappings != null ? propertyMappings.equals( that.propertyMappings ) :
            that.propertyMappings == null;
    }

    private interface SingleMappingByTargetPropertyNameFunction {

        Mapping getSingleMappingByTargetPropertyName(String targetPropertyName);
    }

}

