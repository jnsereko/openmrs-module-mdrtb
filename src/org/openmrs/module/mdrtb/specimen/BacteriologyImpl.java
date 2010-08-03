package org.openmrs.module.mdrtb.specimen;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.mdrtb.MdrtbUtil;


public abstract class BacteriologyImpl extends TestImpl implements Bacteriology {

	
	public Concept getResult() {
    	Obs obs = MdrtbUtil.getObsFromObsGroup(getResultConcept(), test);
    	
    	if (obs == null) {
    		return null;
    	}
    	else {
    		return obs.getValueCoded();
    	}
    }
	
	 public void setResult(Concept result) {
	    	Obs obs = MdrtbUtil.getObsFromObsGroup(getResultConcept(), test);
	    	
	    	 // if this obs have not been created, and there is no data to add, do nothing
			if (obs == null && result == null) {
				return;
			}
	    	
			// if we are trying to set the obs to null, simply void the obs
			if(result == null && StringUtils.isBlank(obs.getComment())) {  // we also need to make sure that there is no comment on the obs in this case
				obs.setVoided(true);
				obs.setVoidReason("voided by Mdr-tb module specimen tracking UI");
				return;
			}
			
	    	// initialize the obs if we need to
			if (obs == null) {
				obs = new Obs (test.getPerson(), getResultConcept(), test.getObsDatetime(), test.getLocation());
				obs.setEncounter(test.getEncounter());
				test.addGroupMember(obs);
			}
			
			// now save the data
			obs.setValueCoded(result);
	    }
	
	
	
	private Concept getResultConcept() {
		if(getTestType() == "smear") {
			return mdrtbFactory.getConceptSmearResult();
		}
		else if(getTestType() == "culture") {
			return mdrtbFactory.getConceptCultureResult();
		}
		else {
			throw new RuntimeException("Invalid test type for bacteriology.");
		}
	}
}
