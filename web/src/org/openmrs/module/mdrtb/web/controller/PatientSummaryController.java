package org.openmrs.module.mdrtb.web.controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientProgram;
import org.openmrs.api.context.Context;
import org.openmrs.module.mdrtb.MdrtbService;
import org.openmrs.module.mdrtb.patient.MdrtbPatientValidator;
import org.openmrs.module.mdrtb.patient.MdrtbPatientWrapper;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/module/mdrtb/summary/summary.form")
public class PatientSummaryController {
	
	protected final Log log = LogFactory.getLog(getClass());

	@ModelAttribute("mdrtbPatient")
	public MdrtbPatientWrapper getMdrtbPatient(@RequestParam(required = true, value="patientId") Integer patientId) {
		return Context.getService(MdrtbService.class).getMdrtbPatient(patientId);
	} 
	
	@InitBinder
	public void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
		
		//bind dates
		SimpleDateFormat dateFormat = Context.getDateFormat();
    	dateFormat.setLenient(false);
    	binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat,true));
		
	}
	
	@SuppressWarnings("unchecked")
    @RequestMapping(method = RequestMethod.GET)
	public ModelAndView showPatientSummary(@ModelAttribute("mdrtbPatient") MdrtbPatientWrapper mdrtbPatient,
	                                       @RequestParam(required = true, value="patientId") Integer patientId, 
	                                       ModelMap map) {
		
		map.put("patientId",patientId);
	
		// if the patient has never been enrolled, show the enrollment page
		if(!mdrtbPatient.everEnrolledInMdrtbProgram()) {
			return new ModelAndView("/module/mdrtb/summary/enrollInProgram", map);
		}
		// otherwise, show the patient overview
		else {
			return new ModelAndView("/module/mdrtb/summary/patientSummary", map);
		}
	}
	


	// handle the "enroll in program" submittal
	@SuppressWarnings("unchecked")
    @RequestMapping(method = RequestMethod.POST, params="dateEnrolled")
	public ModelAndView processEnrollment(@ModelAttribute("mdrtbPatient") MdrtbPatientWrapper mdrtbPatient,
		                                  BindingResult result, SessionStatus status, HttpServletRequest request,
	                                      @RequestParam(required = true, value="dateEnrolled") Date dateEnrolled,
	                                      @RequestParam(required = true, value="patientId") Integer patientId,
	                                      ModelMap map) {
			
		map.put("patientId", patientId);
		
		// validate the enrollment date
		
		if(dateEnrolled == null) {
			result.reject("mdrtb.errors.noEnrollmentDate", "Please specify an enrollment date.");
		}
		else if (dateEnrolled.after(new Date())) {
			result.reject("mdrtb.errors.enrollmentDateInFuture","The enrollment date should not be in the future.");
		}
		
		if (result.hasErrors()) {
			map.put("errors", result);
			return new ModelAndView("/module/mdrtb/summary/enrollInProgram", map);
		}
		 
		// enroll the patient in the program
		mdrtbPatient.enrollInMdrtbProgram(dateEnrolled);
		
		
		// clears the command object from the session
		status.setComplete();
		
		return new ModelAndView("/module/mdrtb/summary/patientSummary", map);

	}
	
	// handle all other submittals
	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView processSubmit(@ModelAttribute("mdrtbPatient") MdrtbPatientWrapper mdrtbPatient,
	                                  BindingResult errors, SessionStatus status, HttpServletRequest request,
	                                  @RequestParam(required = true, value="patientId") Integer patientId, ModelMap map) {
		 
		map.put("patientId", patientId);
		
		if(mdrtbPatient != null) {
			new MdrtbPatientValidator().validate(mdrtbPatient, errors);
    	}
		
		if (errors.hasErrors()) {
			map.put("errors", errors);
			log.error("There are errors.");
			return new ModelAndView("/module/mdrtb/summary/patientSummary", map);
		}
		 
		// save any changes to the mdrtb patient programs
		for(PatientProgram mdrtbProgram : mdrtbPatient.getMdrtbPrograms()) {
			Context.getProgramWorkflowService().savePatientProgram(mdrtbProgram);
		}
		
		// TODO: need to save any other fields that may get added to this form
		
		// clears the command object from the session
		status.setComplete();
		
		return new ModelAndView("/module/mdrtb/summary/patientSummary", map);
	}
}
