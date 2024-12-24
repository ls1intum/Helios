@org.hibernate.annotations.FilterDef(
    name = "gitRepositoryFilter",
    parameters = @org.hibernate.annotations.ParamDef(name = "repository_id", type = Long.class),
    defaultCondition = "repository_id = :repository_id")
package de.tum.cit.aet.helios;
