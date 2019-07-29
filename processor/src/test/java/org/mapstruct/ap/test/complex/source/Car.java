/*
 * Copyright MapStruct Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.mapstruct.ap.test.complex.source;

import java.util.Date;
import java.util.List;

public class Car {

    private Make make;
    private int numberOfSeats;
    private Date manufacturingDate;
    private Person driver;
    private List<Person> passengers;
    private int price;
    private Category category;

    public Car() {
    }

    public Car(String make, int numberOfSeats, Date manufacturingDate, Person driver, List<Person> passengers) {
        this.make = new Make(make);
        this.numberOfSeats = numberOfSeats;
        this.manufacturingDate = manufacturingDate;
        this.driver = driver;
        this.passengers = passengers;
    }

    public Make getMake() {
        return make;
    }

    public void setMake(Make make) {
        this.make = make;
    }

    public int getNumberOfSeats() {
        return numberOfSeats;
    }

    public void setNumberOfSeats(int numberOfSeats) {
        this.numberOfSeats = numberOfSeats;
    }

    public Date getManufacturingDate() {
        return manufacturingDate;
    }

    public void setManufacturingDate(Date manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }

    public Person getDriver() {
        return driver;
    }

    public void setDriver(Person driver) {
        this.driver = driver;
    }

    public List<Person> getPassengers() {
        return passengers;
    }

    public void setPassengers(List<Person> passengers) {
        this.passengers = passengers;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
