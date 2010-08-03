package org.openmrs.module.mdrtb.specimen;

import org.openmrs.Concept;

/**
 * Interface that defines how to interaction with a culture
 * 
 * An implementation of this interface will help us encapsulate the 
 * the messiness of storing the smear data in obsgroups
 */
public interface Culture extends Bacteriology {
	
	/**
	 * Data points this interface provides access to:
	 * 
	 * colonies: the # of colonies present
	 * method: the Concept that represents the method used when performing the test
	 * organism: the type of organism present
	 * 
	 */
	
	public Integer getColonies();
	public void setColonies(Integer colonies);
	
	public Concept getMethod();
	public void setMethod(Concept method);
	
	public Concept getOrganismType();
	public void setOrganismType(Concept organismType);

	public String getOrganismTypeNonCoded();
	public void setOrganismTypeNonCoded(String organismType);

}
