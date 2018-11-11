// sequenceScripts

lineResStr = "";
maindoc = _quote_fcoprocess_document_number;
print "----" + _system_selected_document_number+">>>"+selectedValveItem_quote;

/*if(selectedValveItem_quote == ""){
	return lineResStr;
}*/

parentLineSeqNum="";
parentLineSeqNumCounter="";

parentToGrpLineSeqMap=dict("string"); // key-docno, grpSeqNum
parentToLineNumMap = dict("string"); //key-docno, lineNo
parentToChildActMap = dict("string");

/*
for line in line_fcoProcess {

	put(parentToGrpLineSeqMap,line._document_number, line._group_sequence_number);
	put(parentToLineNumMap, line._document_number, line.lineNumber_line);
	if(line.parentLineNumber_line <> "-1" AND line.parentLineNumber_line <> "" AND line.parentLineNumber_line <> "-" AND line._model_variable_name == "lFPSActuators_LFPS") {
		childActCounter = 0;
		childActCounterStr = get(parentToChildActMap, line.parentLineNumber_line);
		if(childActCounterStr == "") {
			childActCounterStr = "0";
		}
		childActCounter = atoi(childActCounterStr)+1;
		put(parentToChildActMap,line.parentLineNumber_line,string(childActCounter));
	}
}*/

for line in line_fcoProcess{
    linedoc=line._document_number;
    currentLineSeqNum = line.lineNumber_line;
    parentLineDocNum = line.parentLineNumber_line;
    newActSeqNum = 0.0;


    isPart = true;
    isActuator=false;

    parentLineItemNumber="-1";



    //if(linedoc == selectedValveItem_quote) { /// at the parent item row
    if(line.parentLineNumber_line == "-1") {
	    parentLineSeqNum = line.lineNumber_line;
    	parentLineSeqNumCounter = line.lineNumber_line;
    }
	//print line._model_variable_name+"::"+parentLineDocNum  ;
	//if(parentLineDocNum  == selectedValveItem_quote AND line._model_variable_name == "lFPSActuators_LFPS") {
	if(line.parentLineNumber_line <> "-1" AND line.parentLineNumber_line <> "" AND line.parentLineNumber_line <> "-" AND line._model_variable_name == "lFPSActuators_LFPS") {
		if(atof(parentLineSeqNumCounter ) > 0) {
			newActSeqNum = atof(parentLineSeqNumCounter ) + 0.1;
			lineResStr=lineResStr+linedoc+"~lineNumber_line~"+string(newActSeqNum)+"|";
			print lineResStr;
			parentLineSeqNumCounter = string(newActSeqNum);
		}
	}
		/*
	  if(line.parentLineNumber_line <> "-1" AND line.parentLineNumber_line <> "" AND line.parentLineNumber_line <> "-" AND line._model_variable_name == "lFPSActuators_LFPS") {
			parentLineSeqNum = get(parentToLineNumMap, line.parentLineNumber_line);
			if(atof(parentLineSeqNum) > 0) {
				newActSeqNum = atof(parentLineSeqNum) + 0.1;
				lineResStr=lineResStr+linedoc+"~lineNumber_line~"+string(newActSeqNum)+"|";
				parentLineSeqNumCounter = string(newActSeqNum);
			}
		}*/

}
return lineResStr;
