<#--

    Copyright MapStruct Authors.

    Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0

-->
<#list properties as property><@includeModel object=property/><#if property_has_next>, </#if></#list>