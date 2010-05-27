package org.openmrs.module.mdrtb.web.patientsummary;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;


public class PatientSummaryTableFactory {

	private static Log log = LogFactory.getLog(Patient.class);
	
	public static PatientSummaryTable createPatientSummaryTable (Integer patientId, Date startDate, Date endDate){
		
		// TODO: we need to handle "program enrollment" and other key dates for full columns
		
		// create all the column hashes which we will use to generate the table
		// get the hashes for all the columns
		// TODO: map the concept ids dynamically instead of hard-code!
		
		// Map all the Tuberculosis Smear Constructs by month
		Map<Date,Obs> smearHash = getDateToObsMap(patientId,3053);
		// Map all the Tuberculosis Culture Constructs by month
		Map<Date,Obs> cultureHash = getDateToObsMap(patientId,3048);
		// Map all the Drug Sensitivity Test Constructs by month
		Map<Date,Obs> dstHash = getDateToObsMap(patientId,3040);
		
		// create the table
		PatientSummaryTable table = new PatientSummaryTable();
		
		
		// first add the date and bacs
		table.getPatientSummaryTableColumns().add(new PatientSummaryTableColumn("date","Date"));
		table.getPatientSummaryTableColumns().add(new PatientSummaryTableColumn("smear","Smear"));
		table.getPatientSummaryTableColumns().add(new PatientSummaryTableColumn("culture","Culture"));
		
		// now add the dsts
		List<Integer> dstIds = new LinkedList<Integer>();
		initializeDSTs(dstIds);
		for (Integer dstId : dstIds){
			String dstName = Context.getConceptService().getConcept(dstId).getBestShortName(Context.getLocale()).getName();;
			table.getPatientSummaryTableColumns().add(new PatientSummaryTableColumn("dsts." + dstName,dstName ));
		}
		
		
		// now create the actual rows using the hash maps
		
		// if we haven't been given a specific start date, take the first date that we have info for
		if(startDate == null){
			startDate = calculateStartDate(smearHash.keySet(), cultureHash.keySet(), dstHash.keySet());
		}
		
		
		// create the calendar that we are going to use
		Calendar cal = Calendar.getInstance();
		cal.setTime(resetAllButYearAndMonth(startDate));
		
		// iterate through the months
		while(cal.getTime().before(endDate)){
			Date date = cal.getTime();
			
			// create a new row for the table
			PatientSummaryTableRow row = new PatientSummaryTableRow();
			
			// the date row	
			row.setDate(date);
			
			// pull out the Smear and Culture constructs, if any, for this date
			row.setSmear(new PatientSummaryTableBacElement(smearHash.get(date))); // most of these will be null
			row.setCulture(new PatientSummaryTableBacElement(cultureHash.get(date))); // most of these will be null
			
			// DSTS are a bit more complex, as we need to create a hash of drug types to results
			Obs dstTestConstruct = dstHash.get(date);
			Map<String, PatientSummaryTableDSTElement> dsts = new HashMap<String, PatientSummaryTableDSTElement>();
		
			if (dstTestConstruct != null){
				for	(Obs obs : dstTestConstruct.getGroupMembers()) {
					// if this obs is a test result construct, we need to add it to our hash
					if (obs.getConcept().getId() == 3025){
						PatientSummaryTableDSTElement dst = new PatientSummaryTableDSTElement(obs);
					
						// TODO: we need to be able to handle "waiting on results" case
					
						// now loop through all the results to figure out where to hash it
						for (Obs result : obs.getGroupMembers()){
						
							// TODO: this could be handled more elegantly
							// TODO: ***we need to hash all concentrations for a certain drug type--multiple drug types in result***
							Integer resultConceptId = result.getConcept().getConceptId();
							if (resultConceptId == 2474 || resultConceptId == 3017 || resultConceptId == 1441){
								String dstName = result.getValueCoded().getBestShortName(Context.getLocale()).getName();
								// put it in the hash under the drug name
								dsts.put(dstName, dst);
								//addPatientSummaryTableColumnIfNeeded(dstName,table); // create a new dst column if we haven't encountered this dst before
								break; // once we've identified the drug we are done... a single result construct shouldn't have more than one drug!
							}
						}
					}
				}
			}
			
			
			// set the DST hash
			row.setDsts(dsts);
			
			// add this row to the object
			log.error("Adding a row!");
			table.getPatientSummaryTableRows().add(row);
			
			// go to the next month
			cal.add(Calendar.MONTH,1);
		}
		
		return table;
	}
	
	/*;
	 * Utility Functions
	 */
	
	private static Map<Date,Obs> getDateToObsMap(int patientId,int conceptId){
		// first, get all the obs for this patient/concept pair
		// TODO: fix the patient/person id mapping issue???
		List<Obs> obsList = Context.getObsService().getObservationsByPersonAndConcept(Context.getPersonService().getPerson(patientId), Context.getConceptService().getConcept(conceptId));
	
		// now create the map of dates to obs
		Map<Date,Obs> map = new HashMap<Date,Obs>();
		
		// IMPORTANT! needs to handle multiple obs within the same month--right now it just overwrite them, okay for prototype, but not for production
		// IMPORTANT! need to figure out exactly what date we want to use here... encounterDateTime? Obs DateTime?
		for (Obs obs : obsList){
			// put the obs in a hash based on the month and year of the obs
			map.put(resetAllButYearAndMonth(obs.getEncounter().getEncounterDatetime()), obs);
		}
		
		return map;
	}
	
	// returns the earliest date we have any data from
	private static Date calculateStartDate(Set<Date> smearDateSet, Set<Date> cultureDateSet, Set<Date> dstDateSet) {
		
		List<Date> minDates = new ArrayList<Date>();
		minDates.add(Collections.min(smearDateSet));
		minDates.add(Collections.min(cultureDateSet));
		minDates.add(Collections.min(dstDateSet));
		
		return Collections.min(minDates);	
	}
	
	/*
	 * Initialize the DSTs (this of course will be done some other way than hard-coded concept ids in the end)
	 */
	private static void initializeDSTs(List<Integer> dstIds){
		
		// ugly hack until I figure out where I want to pull the DSTs from
		
		dstIds.add(656);
		dstIds.add(745);
		dstIds.add(438);
		dstIds.add(1417);
		dstIds.add(1411);
		dstIds.add(1414);
		dstIds.add(740);
		dstIds.add(767);
		dstIds.add(5829);
		dstIds.add(755);
		dstIds.add(1406);
		dstIds.add(1412);
		dstIds.add(1413);
		dstIds.add(2459);
		dstIds.add(2460);
		dstIds.add(1419);

	}
	
	
	/*
	 * Given a Date, returns a Date which is equal to first day of that month and year at 00:00:00
	 */
	private static Date resetAllButYearAndMonth(Date date){
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		return cal.getTime();
	}
	
	
	/*
	 * Adds a column to PatientSummaryTableColumns if a column with that code doesn't exist
	 */
	
	/* private static void addPatientSummaryTableColumnIfNeeded(String name, PatientSummaryTable table){
		for (PatientSummaryTableColumn column : table.getPatientSummaryTableColumns()) {
			if (StringUtils.equals(column.getCode(), "dsts." + name))
				return;
		}
		// if we've made it this far we need to add the column
		table.getPatientSummaryTableColumns().add(new PatientSummaryTableColumn("dsts." + name, name));
	} */
}
