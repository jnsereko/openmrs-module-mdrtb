package org.openmrs.module.mdrtb.regimen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;

public class RegimenUtils {

    protected final Log log = LogFactory.getLog(getClass());
    
    public static RegimenHistory getRegimenHistory(Patient patient) {
        return new RegimenHistory(Context.getOrderService().getDrugOrdersByPatient(patient));
    }
    
    
    /* here's the logic for what happens if there is a future regimen:
     * 
     * 
     *      //1.  if no drug order of a specific DrugOrder, (create the new order).  (OUTCOME1)
            
            //2.  if DrugOrder, for all DrugOrders: 
                //a.  if new start is before old start 
                    //NEW ORDER HAS STOP DATE -- create the older as is, and make adjustments:
                        //1.  if new end is before or equal to old start (create the new order)  (OUTCOME2)
                        //2.  if new end is after old start and ( before old end or old end is infinite) 
                           //if order is different, adjust the start date of the old order to the end date of the new (create the new order) (OUTCOME3)
                           //if order is same void old order(doesn't matter if old order has infinite stop date or not) (create the new order) (OUTCOME4)
                        //3. if end date is greater than or equal to old end -- void old order   (create the new order)  (OUTCOME5)
                    //NEW ORDER DOESN'T HAVE STOP DATE
                        //4. orders are different
                             //set end date of new to beginning of old and stop iterating over existing drug orders time sequence (create the new order, modified) (OUTCOME6)
                        //5. orders are the same
                            //delete the old order (create the new order) (OUTCOME7)
                //b. if start is the same 
                    // void existing (create the new order) (OUTCOME8)
            
                //c.  if start is after existing start
                    //1. if order is after old drug end  (create the new order) (OUTCOME9)
                    //2. if order is before old drug end or equal to old drug end or old drug end date is infinite
                        //if orders are the same update the old order with the new, taking the new end date value (Do not create new order) (OUTCOME10)
                        //if orders are different adjust the old to end on new start date (create the new order) (OUTCOME11)
                    
     * */
    public static void setRegimen(Patient patient, Date effectiveDate, Collection<DrugOrder> drugOrders, Concept reasonForChange, Encounter encounterForChange) {
        RegimenHistory history = getRegimenHistory(patient);
        Regimen regOnDate = history.getRegimen(effectiveDate);
        List<Regimen> regAfterDate = history.getRegimensAfter(effectiveDate);
        OrderService os = Context.getOrderService();
        if (encounterForChange != null){
            Context.getEncounterService().saveEncounter(encounterForChange);
            for (DrugOrder drugOrder : drugOrders) {
                drugOrder.setEncounter(encounterForChange);
            }
        }    
         
        if (!anyRegimens(regAfterDate)) {
            if (regOnDate == null || regOnDate.getComponents().isEmpty()) {
                //case:  there is no existing regimen on the regimen start date, and there are no new regimens after this date
                // go ahead and create the regimen:
                for (DrugOrder drugOrder : drugOrders) {
                    Context.getOrderService().saveOrder(drugOrder);
                }
            } else {

                //case: there are still open orders and there are no new regimens after this date
                // first see what existing things we need to stop
                for (RegimenComponent before : regOnDate.getComponents()) {
                    //stop the old order only if it isn't exactly identical to a new order (excluding discontinued_date)
                    for (DrugOrder newOrder:drugOrders){
                        if (!before.getDrugOrder().getDiscontinued() && drugOrderMatchesDrugConcept(before.getDrugOrder(), newOrder) && !regimenComponentIsTheSameAsDrugOrderExcludingDates(before.getDrugOrder(), newOrder)){
                            discontinueOrder( before.getDrugOrder(), effectiveDate, reasonForChange);
                        }    
                    }
                }
                // now see what new things to start (or extend)
                for (DrugOrder newOrder : drugOrders) {
                    
                    // create a new order if there isn't already an existing match, 
                        //or if there is (excluding discontinued date) you need to extend, or null the stop date
                    
               
                        boolean alreadyExists = false;
                        for (RegimenComponent before : regOnDate.getComponents()){
                            
                            if (!before.getDrugOrder().getDiscontinued() && regimenComponentIsTheSameAsDrugOrderExcludingDates(before.getDrugOrder(), newOrder)){
                                alreadyExists = true;
                                before.getDrugOrder().setDiscontinuedDate(newOrder.getDiscontinuedDate());
                                before.getDrugOrder().setAutoExpireDate(newOrder.getAutoExpireDate());
                                before.getDrugOrder().setPrn(newOrder.getPrn());
                                before.getDrugOrder().setInstructions(newOrder.getInstructions());
                                os.saveOrder(before.getDrugOrder());
                                newOrder.setOrderId(before.getDrugOrder().getOrderId());
                                break;
                            }
                        }
                        if (!alreadyExists){
                            os.saveOrder(newOrder);
                        }
                }
            }
        } else { //there is a regimen change after the new drug order start date
            for (DrugOrder newOrder : drugOrders) {
                  boolean saveOrder = false;
                  boolean merged = false;
                  history = getRegimenHistory(patient);
                  List<DrugOrder> existingDrugOrders = getDrugOrdersInOrderByDrugOrConcept(history, newOrder);                                  
                  if (existingDrugOrders.size() == 0){
                          saveOrder = setSaveOrder(merged); //(OUTCOME1)
                  } else { 
                        for (DrugOrder before : existingDrugOrders){ 
                            if (newOrder.getStartDate().before(before.getStartDate())){ 
                                    if (newOrder.getDiscontinuedDate() != null){
                                        if (newOrder.getDiscontinuedDate().before(before.getStartDate()) || newOrder.getDiscontinuedDate().equals(before.getStartDate())){
                                            saveOrder = setSaveOrder(merged);//(OUTCOME2)
                                        } else if (newOrder.getDiscontinuedDate().after(before.getStartDate()) && (before.getDiscontinuedDate() == null || newOrder.getDiscontinuedDate().before(before.getDiscontinuedDate()))){
                                            if (!regimenComponentIsTheSameAsDrugOrderExcludingDates(before, newOrder)){
                                                //(OUTCOME3)
                                                before.setStartDate(newOrder.getDiscontinuedDate());
                                                os.saveOrder(before);
                                                saveOrder = setSaveOrder(merged);
                                            } else {
                                                //(OUTCOME4)    
                                                os.voidOrder(before, "overwritten");
                                                saveOrder = setSaveOrder(merged);
                                            }   
                                        } else if (before.getDiscontinuedDate() != null && (newOrder.getDiscontinuedDate().after(before.getDiscontinuedDate()) || newOrder.getDiscontinuedDate().equals(before.getDiscontinuedDate()))){
                                                //(OUTCOME5)
                                                os.voidOrder(before, "overwritten");
                                                saveOrder = setSaveOrder(merged);
                                        }
                                    } else {//new order has infinite end date
                                        if (!regimenComponentIsTheSameAsDrugOrderExcludingDates(before, newOrder)){
                                          //(OUTCOME6)
                                                newOrder.setDiscontinuedDate(before.getStartDate());
                                                saveOrder = setSaveOrder(merged);
                                                break;
                                        } else {
                                          //(OUTCOME7)
                                            os.voidOrder(before, "overwritten");
                                            saveOrder = setSaveOrder(merged);
                                        }
                                    }         
                            } else if (newOrder.getStartDate().equals(before.getStartDate())){ //b
                                //(OUTCOME8)
                                os.voidOrder(before, "overwritten");
                                saveOrder = setSaveOrder(merged);
                            } else { //c -- start date is after or equal to old end date
                                if (before.getDiscontinuedDate() != null && newOrder.getStartDate().after(before.getDiscontinuedDate()))//1
                                    //(OUTCOME9)
                                    saveOrder = setSaveOrder(merged);
                                    
                                if (before.getDiscontinuedDate() == null || newOrder.getStartDate().before(before.getDiscontinuedDate()) || newOrder.getStartDate().equals(before.getDiscontinuedDate())){//2
                                    if (regimenComponentIsTheSameAsDrugOrderExcludingDates(before, newOrder)){
                                      //(OUTCOME10)  
                                        before.setDiscontinuedDate(newOrder.getDiscontinuedDate());
                                        before.setAutoExpireDate(newOrder.getAutoExpireDate());
                                        before.setPrn(newOrder.getPrn());
                                        before.setInstructions(newOrder.getInstructions());
                                        os.saveOrder(before);
                                        saveOrder = false;
                                        newOrder.setOrderId(before.getOrderId());
                                        merged = true;
                                    } else {
                                      //(OUTCOME11)  
                                        before.setDiscontinuedDate(newOrder.getStartDate());
                                        os.saveOrder(before);
                                        saveOrder = setSaveOrder(merged);
                                    }
                                }
                            } 
                        }
                  }
                  if (saveOrder)
                      os.saveOrder(newOrder);
            }      
        }
    }

    /**
     * Discontinues an order given a date and a reason, and saves it to the database if anything has changed.
     *  
     * @param order
     * @param effectiveDate
     * @param reason
     * 
     * @should change discontinued metadata if order is set to be discontinued after date
     * @should have no effect if order is discontinued before date
     */
    public static void discontinueOrder(Order order, Date date, Concept reason) {
        if (!order.isDiscontinuedRightNow()) {
            order.setDiscontinued(true);
            order.setDiscontinuedDate(date);
            order.setDiscontinuedReason(reason);
            Context.getOrderService().saveOrder(order);
        } else if (OpenmrsUtil.compareWithNullAsLatest(date, order.getDiscontinuedDate()) < 0) {
            order.setDiscontinued(true); // should already be true
            order.setDiscontinuedDate(date);
            order.setDiscontinuedReason(reason);
            Context.getOrderService().saveOrder(order);
        }
    }

    /**
     * Returns true if there are any non-empty regimens in this list
     * 
     * @param afterDate
     * @return
     */
    private static boolean anyRegimens(List<Regimen> regimenList) {
        for (Regimen reg : regimenList)
            if (!reg.getComponents().isEmpty())
                return true;
        return false;
    }
    
    private static boolean regimenComponentIsTheSameAsDrugOrderExcludingDates(DrugOrder rc, DrugOrder doTmp){
        if (rc.getDrug() != null && doTmp.getDrug() != null && rc.getDrug().getDrugId().intValue() != doTmp.getDrug().getDrugId().intValue())
            return false;
        if (!OpenmrsUtil.nullSafeEquals(rc.getConcept(), doTmp.getConcept()))
            return false; 
        if (!OpenmrsUtil.nullSafeEquals(rc.getDose(), doTmp.getDose()))
            return false;
        if (!OpenmrsUtil.nullSafeEquals(rc.getFrequency(), doTmp.getFrequency()))
            return false;    
        if (!OpenmrsUtil.nullSafeEquals(rc.getUnits(), doTmp.getUnits()))
            return false;    
        return true;
    }
    
    
    private static boolean drugOrderMatchesDrugConcept(DrugOrder rc, DrugOrder doTmp){
        if (rc.getDrug() != null && 
                (doTmp.getDrug() != null && rc.getDrug().getConcept().equals(doTmp.getDrug().getConcept())
                ||
                (doTmp.getConcept() != null && rc.getDrug().getConcept().equals(doTmp.getConcept()))
                )
            )
            return true;
        if (doTmp.getDrug() != null && 
                (rc.getDrug() != null && doTmp.getDrug().getConcept().equals(rc.getDrug().getConcept())
                ||
                (rc.getConcept() != null && doTmp.getDrug().getConcept().equals(rc.getConcept()))
                )
            )
            return true;
        if (doTmp.getConcept() != null && rc.getConcept() != null && doTmp.getConcept().equals(rc.getConcept()))
            return true;
        return false;
    }
    
    /*newDrugOrder argument used to pass in the drug or the drug concept*/
    public static List<DrugOrder> getDrugOrdersInOrderByDrugOrConcept(RegimenHistory history, DrugOrder newDrugOrder){
            List<DrugOrder> ret = new ArrayList<DrugOrder>();
            List<Regimen> regList = history.getRegimenList();
            
            for (Regimen regimen : regList){
                for (RegimenComponent rc : regimen.getComponents()){
                    if (drugOrderMatchesDrugConcept(rc.getDrugOrder(), newDrugOrder)){
                        ret.add(rc.getDrugOrder());   
                    }
                }
            }
            return ret;
    }
    
    
    private static boolean setSaveOrder(boolean merged){
        if (!merged)
            return true;
        else 
            return false;
    }
    
    public static String getRegimenAsString(Date regDate, Patient p, String separator, boolean includeDosages) {
    	return getRegimenAsString(getRegimenOnDate(p, regDate), separator, includeDosages);
    }
    
    public static String getRegimenAsString(Regimen r, String separator, boolean includeDosages) {
    	if (separator == null) {
    		separator = "";
    	}
		String ret = "";
		if (r != null && r.getComponents()!= null){
		    int total = r.getComponents().size();
		    int count = 1;
		    for (RegimenComponent rc : r.getComponents()){
		        if (rc.getDrug() == null)
		            ret += rc.getGeneric().getBestShortName(Context.getLocale());
		        else
		            ret += rc.getDrug().getName();
		        if (includeDosages)
		            ret += " (" + rc.getDrugOrder().getDose() + " " + rc.getDrugOrder().getUnits()+ " " + rc.getDrugOrder().getFrequency() + ")";
		        if (count != total )
		            ret += separator;
		        count ++;
		    }
		}   
        return ret;
    }
    
    public static Regimen getRegimenOnDate(Patient p, Date regDate){
        Regimen ret = null;
        RegimenHistory rh = RegimenUtils.getRegimenHistory(p);
        if (rh != null){
            Regimen r = rh.getRegimen(regDate);
            if (r != null){
               return r;
            }   
        }
        return ret;
    }
}












