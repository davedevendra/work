package com.ddave.service.employee.entity;

import java.util.Date;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value="EclipseLink-2.5.2.v20140319-rNA", date="2018-05-07T22:22:15")
@StaticMetamodel(AcmeEmployee.class)
public class AcmeEmployee_ { 

    public static volatile SingularAttribute<AcmeEmployee, String> firstName;
    public static volatile SingularAttribute<AcmeEmployee, String> lastName;
    public static volatile SingularAttribute<AcmeEmployee, Integer> jobId;
    public static volatile SingularAttribute<AcmeEmployee, Integer> id;
    public static volatile SingularAttribute<AcmeEmployee, Date> startDate;
    public static volatile SingularAttribute<AcmeEmployee, Integer> age;
    public static volatile SingularAttribute<AcmeEmployee, String> status;

}