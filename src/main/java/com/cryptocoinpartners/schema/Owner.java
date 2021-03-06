package com.cryptocoinpartners.schema;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.Collection;


/**
 * An Owner is a person or corporate entity who holds Stakes in Funds
 * @author Tim Olson
 */
@Entity
public class Owner extends EntityBase {


    public Owner(String name) {
        this.name = name;
    }


    @Basic(optional = false)
    public String getName() { return name; }


    @OneToMany
    public Collection<Stake> getStakes() { return stakes; }


    protected Owner() {}
    protected void setName(String name) { this.name = name; }
    protected void setStakes(Collection<Stake> stakes) { this.stakes = stakes; }


    private Collection<Stake> stakes;
    private String name;
}
