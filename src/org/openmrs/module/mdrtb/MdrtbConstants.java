package org.openmrs.module.mdrtb;

public class MdrtbConstants {
    private static final String moduleName = "mdrtb";
    public static final String MDRTB_PROGRAM_GLOBAL_PROPERTY = moduleName + ".program_name";
    public static final String ROLES_TO_REDIRECT_GLOBAL_PROPERTY = moduleName + ".roles_to_redirect_from_openmrs_homepage";
    public static final String MDRTB_PATIENT_IDENTIFIER_TYPES = moduleName + ".patient_identifier_type_list";    
    
    public static enum MdrtbPatientDashboardTabs{
    	SUMMARY("summary","mdrtb.summary"),
    	STATUS("status","mdrtb.status"),
    	FORM("formEntry","mdrtb.formentry"),
    	REG("patientRegimen","mdrtb.patientregimen"),
    	BAC("BAC","mdrtb.bacteriologies"),
    	DST("DST","mdrtb.dsts"),
    	CONTACTS("contacts","mdrtb.contacts");
    	
    	String id; // the id to reference the code in Javascript
    	String messageCode;  // the spring:message code for the tag
    	
    	MdrtbPatientDashboardTabs(String id, String messageCode){
    		this.id = id;
    		this.messageCode = messageCode;
    	}
    	public String getId(){
    		return id;
    	}
    	public String getMessageCode(){
    		return messageCode;
    	}
    }
}



