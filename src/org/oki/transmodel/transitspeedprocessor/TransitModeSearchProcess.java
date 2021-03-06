package org.oki.transmodel.transitspeedprocessor;

import java.util.ArrayList;
import java.util.concurrent.Callable;

public class TransitModeSearchProcess implements Callable {

	private TransitGPSData tGPS;
	private ArrayList<TransitAssignmentLinks> tALinks;
	private ArrayList<TransitSurveyAssignmentData> tsad;
	
	TransitModeSearchProcess(TransitGPSData tGPS,ArrayList<TransitAssignmentLinks> tALinks,ArrayList<TransitSurveyAssignmentData> tsad){
		this.tGPS=tGPS;
		this.tALinks=tALinks;
		this.tsad=tsad;
	}
	
	@Override
	public Object call() throws Exception{
		for(TransitAssignmentLinks tal:tALinks){
			if(tGPS.FileID==tal.fileId){
				for(TransitSurveyAssignmentData ts:tsad){
					if(tal.assignmentID==ts.assignmentID){
						tGPS.Mode=ts.Mode;
						return ts.Mode;
					}
				}
			}
		}
		
		
		return null;
	}
}
