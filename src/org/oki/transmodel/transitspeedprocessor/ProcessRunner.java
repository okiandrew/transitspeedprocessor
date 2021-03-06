package org.oki.transmodel.transitspeedprocessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.hexiong.jdbf.DBFWriter;
import com.hexiong.jdbf.JDBFException;
import com.hexiong.jdbf.JDBField;
import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;

public class ProcessRunner {

	/**
	 * @param args
	 */
	public static Properties config;
	public static void main(String[] args) throws IOException {
		//Read Control File
		config=new Properties();
		config.load(ProcessRunner.class.getClassLoader().getResourceAsStream("TransitSpeedProcessor.properties"));
		
		ArrayList<TransitGPSData> transitGPS=new ArrayList<TransitGPSData>();
		ArrayList<TransitGPSData> transitGPSClean=new ArrayList<TransitGPSData>();
		ArrayList<NodeData> nodeData=new ArrayList<NodeData>();
		ArrayList<NetworkData> AMNetData=new ArrayList<NetworkData>();
		ArrayList<NetworkData> MDNetData=new ArrayList<NetworkData>();
		ArrayList<TransitSurveyAssignmentData> surveyAssignments = new ArrayList<TransitSurveyAssignmentData>();
		ArrayList<TransitAssignmentLinks> surveyAssignLinks = new ArrayList<TransitAssignmentLinks>();
		
		/*
		 * Read input files
		 */
		try{
			//Read GPS Input Points
			String[] GPSPointInputFieldMap={"fileeid","pdatime","latit","longit","x","y","n","timesec"};
			ArrayList<Object[]> tempGPSData = new ArrayList<Object[]>();
			tempGPSData=readDBF(config.getProperty("TransitGPSTable"),GPSPointInputFieldMap);
			for(Object o[]:tempGPSData)
				transitGPS.add(new TransitGPSData(((Double)o[0]).intValue(),o[1],(Double)o[2],(Double)o[3],(Double)o[4],(Double)o[5],((Double)o[6]).intValue(),((Double)o[7]).intValue()));
			tempGPSData=null; //Cleanup
			GPSPointInputFieldMap=null; //Cleanup
			
			//Read Nodes
			String[] nodeInputFieldMap={"n","x","y"};
			ArrayList<Object[]> tempNodeData=new ArrayList<Object[]>();
			tempNodeData=readDBF(config.getProperty("NodeTable"),nodeInputFieldMap);
			for(Object o[]:tempNodeData){
				Object t[]=new Object[o.length];
				if(o[0] instanceof Double)
					t[0]=((Double)o[0]).intValue();
				else
					t[0]=((Float)o[0]).intValue();
				
				for(int c=1;c<o.length;c++){
					if(o[c] instanceof Double)
						t[c]=(Double)o[c];
					else if(o[c] instanceof Float)
						t[c]=((Float)o[c]).doubleValue();
				}
				nodeData.add(new NodeData((int)t[0],(Double)t[1],(Double)t[2]));
			}
			tempNodeData=null;
			nodeInputFieldMap=null;
			
			//Read AM Network
			String[] netInputFieldMap={"a","b","admclass","cspd_1","areatype","lanes"};
			ArrayList<Object[]> tempNetData=new ArrayList<Object[]>();
			tempNetData=readDBF(config.getProperty("AMLinkTable"),netInputFieldMap);
			for(Object o[]:tempNetData)
				AMNetData.add(new NetworkData(((Double)o[0]).intValue(),((Double)o[1]).intValue(),((Double)o[2]).intValue(),(Double)o[3],((Double)o[4]).intValue(),((Double)o[5]).intValue()));
			tempNetData=null;
			
			//Read MD Network
			tempNetData=new ArrayList<Object[]>();
			tempNetData=readDBF(config.getProperty("MDLinkTable"),netInputFieldMap);
			for(Object o[]:tempNetData)
				MDNetData.add(new NetworkData(((Double)o[0]).intValue(),((Double)o[1]).intValue(),((Double)o[2]).intValue(),(Double)o[3],((Double)o[4]).intValue(),((Double)o[5]).intValue()));
			tempNetData=null;
			netInputFieldMap=null;
			
		}catch (FileNotFoundException e){
			System.out.println("FILE NOT FOUND");
			System.out.println(e.getLocalizedMessage());
			e.printStackTrace();
		}catch (DBFException e){
			System.out.println("UNSPECIFIED DBF ERROR");
			e.printStackTrace();
		}catch (Exception e){
			System.out.println("ERROR!");
			e.printStackTrace();
		}
		
		try{
			String[] RideCountDataMap={"assignment","triporder","route","direction","tod"};
			ArrayList<Object[]> tempRideCountList=new ArrayList<Object[]>();
			tempRideCountList=readExcel(config.getProperty("AssignmentTable"),"assignments",RideCountDataMap);
			for(Object o[]:tempRideCountList)
				surveyAssignments.add(new TransitSurveyAssignmentData((int)Double.parseDouble((String) o[0]),(int)Double.parseDouble((String) o[1]),(String) o[2],(String) o[3],(String) o[4]));		
			tempRideCountList=null;
			RideCountDataMap=null;
			
			String[] TripLinkDataMap={"fileid","filename"};
			ArrayList<Object[]> tempTripLink=new ArrayList<Object[]>();
			tempTripLink=readExcel(config.getProperty("GPSLinkTable"),"gpsfiles",TripLinkDataMap);
			for(Object o[]:tempTripLink)
				surveyAssignLinks.add(new TransitAssignmentLinks((int)Double.parseDouble((String)o[0]),(String)o[1]));
			tempTripLink=null;
			TripLinkDataMap=null;
			
		}catch (InvalidFormatException e){
			System.out.println("THE FORMAT IS ILLEGAL. The FBI is on the way.  Your ass is grass!");
			e.printStackTrace();
		}catch(FileNotFoundException e){
			System.out.println("FILE NOT FOUND");
			e.printStackTrace();
		}catch(IOException e){
			System.out.println("IOEXCEPTION");
			e.printStackTrace();
		}catch(Exception e){
			System.out.println("ERROR");
			e.printStackTrace();
		}
		System.out.println("Completed reading files");
		
		/*
		 * Distance to N
		 */
		Date timeNow=new Date();
		System.out.println("Starting DistToN at "+timeNow.toString());
		/*
		 * Single-core performance - 14sec for 10,000 items
		 * 3-core performance - 1 sec for 10,000 items
		 * 3-core performance - 1 sec for 100,000 items
		 */
		new DistToN(transitGPS,nodeData).run();
		timeNow=new Date();
		System.out.println("Finishing DistToN at "+timeNow.toString());
		
		
		/*
		 *  Nearest N
		 */
		timeNow=new Date();
		System.out.println("Starting Nearest-N at "+timeNow.toString());
		/*
		 * Single-core performance - 1 second for 10k records
		 * Less than a second on a quad-core
		 * Single-core performance - 1:28 for 100k records
		 * Three-core performance - 0:30 for 100k records
		 * Three-core performance - 13:57 for all records
		 * Seven-core performance 64b - 17:58 for all records
		 */
		new NearestN(transitGPS).run();
		timeNow=new Date();
		System.out.println("Completed Nearest-N operation at "+timeNow.toString());
		// End Nearest N

		System.out.println("There are "+transitGPS.size()+" records");
		
		//Cleanup
		for(Iterator<TransitGPSData> iter=transitGPS.iterator(); iter.hasNext();){
			TransitGPSData t=iter.next();
			if(t.removeMe)
				iter.remove();
		}
		System.out.println("There are "+transitGPS.size()+" records");
		
		//TODO: Point on *which* route? Agency Codes?  Crosstowns?
		//Update Transit GPS with mode
		timeNow=new Date();
		System.out.println("Starting linking process at "+timeNow.toString());
		new TransitModeSearch(transitGPS,surveyAssignLinks,surveyAssignments);
		timeNow=new Date();
		System.out.println("Completed linking process at "+timeNow.toString());
		
		//TODO: Output the linked records to check.
		
		//FIXME: Remove when debugging is complete (this is the last holding step)
		int a=1;
		System.out.println(a);
	}
	
	
	/**
	 * Reads the selected contents of a DBF and stuffs them into an arraylist of arrays of objects
	 * @param DBFFileName The file path to the DBF to read
	 * @param inputFieldMap A String array of fields to read
	 * @return An Arraylist of Object arrays loaded with the contents
	 * @throws FileNotFoundException if the DBF file is not found
	 * @throws DBFException if there is a different problem with the DBF
	 */
	static ArrayList<Object[]> readDBF(String DBFFileName, String[] inputFieldMap) throws FileNotFoundException, DBFException{
		//TODO: Remove the javadbf library and move to jdbf (which actually works for writing)
		//      This works fine for reading; removing it is for the sake of simplicity and organization
		InputStream iStream = new FileInputStream(DBFFileName);
		DBFReader reader=new DBFReader(iStream);
		int numberOfFields=reader.getFieldCount();
		ArrayList<Object[]> outObj=new ArrayList<Object[]>();
		
		Object[] row;
		int rowCount=0;
		while((row=reader.nextRecord())!=null){
			rowCount++;
			if((rowCount % 100)==0 || rowCount==1)
				System.out.println("Reading DBF row "+rowCount);
			Object[] newObject = new Object[inputFieldMap.length];
			for(int f=0;f<numberOfFields;f++){
				DBFField field=reader.getField(f);
				for(int ff=0;ff<inputFieldMap.length;ff++)
					if(field.getName().toLowerCase().equals(inputFieldMap[ff].toLowerCase())){
						newObject[ff]=row[f];
					}
			}
			outObj.add(newObject);
			//if(rowCount==100000) //FIXME: For debugging only
				//break;
		}
		return outObj;
	}
	
	/**
	 * Reads and Excel file (assuming the first row is a header row) and puts all the data into an arraylist of object arrays.
	 * @param ExcelFileName The Excel file to read.  Wants an XLSX file.
	 * @param SheetName The Excel sheetname to read.  If empty or null, uses sheet 0.
	 * @param inputFieldMap A map of fields to look for
	 * @return An arraylist of object arrays with the data
	 * @throws InvalidFormatException
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	static ArrayList<Object[]> readExcel(String ExcelFileName, String SheetName, String[]inputFieldMap) throws InvalidFormatException, FileNotFoundException, IOException{
		OPCPackage pkg=OPCPackage.open(new FileInputStream(ExcelFileName));
		XSSFWorkbook wb=new XSSFWorkbook(pkg);		
		Sheet sheet;
		if(SheetName.equals("") || SheetName==null)
			sheet=wb.getSheetAt(0);
		else
			sheet=wb.getSheet(SheetName);
		//Header Row
		int[] cols=new int[inputFieldMap.length];
		int ifmCol=0;
		Row r=sheet.getRow(0);
		for(Cell c:r){
			for(int x=0;x<inputFieldMap.length;x++){
				if(c.toString().toLowerCase().equalsIgnoreCase(inputFieldMap[x])){
					cols[ifmCol]=c.getColumnIndex();
					ifmCol++;
					break;
				}
			}
		}
		
		ArrayList<Object[]> outAL=new ArrayList<Object[]>();
		
		for(Row row:sheet){
			Object[] out=new Object[inputFieldMap.length];
			if(row.getRowNum()>0){
				if(row.getRowNum()%1000==0)
					System.out.println("Reading Excel file, row "+row.getRowNum());
				boolean addMe=false;
				for(int x=0;x<cols.length;x++){
					if(row.getCell(cols[x])!=null){
						out[x]=row.getCell(cols[x]).toString();
						addMe=true;
					}
				}
				if(addMe)
					outAL.add(out);
			}
		}
		return outAL;
	}	

	/**
	 * Writes an object out to a DBF
	 * @param DBFFileName The file path and name to write the DBF to
	 * @param objectToWrite The object to write
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws IOException
	 * @throws JDBFException
	 */
	static void writeDBF(String DBFFileName, ArrayList<?> objectToWrite) throws IllegalArgumentException, IllegalAccessException, IOException, JDBFException{
		Object o=objectToWrite.get(0);
		Class c=o.getClass();
		Field[] cdf=c.getDeclaredFields();
		JDBField[] jdbFields=new JDBField[cdf.length];
		int fldCount=0;
		String fieldDefs[]=new String[cdf.length];
		for(Field f:cdf){
			switch(f.getType().toString()){
			case "int":
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'N',20,0);
				fieldDefs[fldCount]="N";
				break;
			case "double":
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'F',20,8);
				fieldDefs[fldCount]="F";
				break;
			case "java.lang.String":
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'C',32,0);
				fieldDefs[fldCount]="C";
				break;
			case "java.util.Date":
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'D',8,0);
				fieldDefs[fldCount]="D";
				break;
			case "boolean":
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'C',6,0);
				fieldDefs[fldCount]="C";
				break;
			default:
				jdbFields[fldCount]=new JDBField(f.getName().substring(0, Math.min(10,f.getName().length())),'C',32,0);
				fieldDefs[fldCount]="C";
			break;
			}
			fldCount++;
		}
		
		DBFWriter writer=new DBFWriter(DBFFileName,jdbFields);

		for(Object o2:objectToWrite){
			Object[] rowData=new Object[fldCount];
			Class c2=o2.getClass();
			Field[] fwa=c2.getDeclaredFields();
			int fldCnt=0;
			 //C, D, F, N
			for(Field fw:fwa){
				if(fw.get(o2)!=null){
					switch(fieldDefs[fldCnt]){
						case("C"):
							rowData[fldCnt]=fw.get(o2).toString();
							break;
						case("D"):
							rowData[fldCnt]=fw.get(o2);
							break;
						case("L"):
							rowData[fldCnt]=fw.get(o2);
							break;
						case("N"):
							rowData[fldCnt]=Double.parseDouble(fw.get(o2).toString());
							break;
						case("F"):
							rowData[fldCnt]=Double.parseDouble(fw.get(o2).toString());
							break;
						default:
							rowData[fldCnt]=fw.get(o2);
							break;
					}
				}else
					rowData[fldCnt]="";
				fldCnt++;
			}
			writer.addRecord(rowData);		
		}
		writer.close();
	}
}
