lineResStr = "";
maindoc = _quote_fcoprocess_document_number;
multiValue=0.88;
mfgBUValue = "Lynchburg";
leadTime = finalDeliveryDate_quote;

altValue = "P";

parentSpareDocumentMap=dict("string");
parentSpareDocumentToCountMap = dict("string");

//print "----" + _system_selected_document_number+">>>"+selectedValveItem_quote;

for line in line_fcoProcess{

    //lineNo=0;
    linedoc=line._document_number;

    tagNo="";
    modelName="";
    modelDesc="";

    businessUnit="";
    isPart = true;
    isActuator=false;

    //print line._config_attr_info;
    //print line._model_variable_name;
    //print _system_selected_document_number;

    parentLineItemNumber="-";
    if(linedoc == selectedValveItem_quote) {
        parentLineItemNumber="-1";
    } else {
        if(selectedValveItem_quote <> "") {
          parentLineItemNumber = selectedValveItem_quote;
        } else {
          parentLineItemNumber = line.parentLineNumber_line;
        }
    }




    //Check whether model line item or part line item
    // if(len(line._model_variable_name)>0){
    if(line._model_variable_name == "null" OR len(line._model_variable_name)>0) {
        isPart = false;
    }else{
        isPart = true;
    }
    //print isPart;


    ///////////
    //TMBV
    ///////////

    if(line._model_variable_name == "valbartTMBV") {
        //tagNo=getconfigattrvalue(line._document_number, "tagNumber_PF");
        tagNo=getconfigattrvalue(line._document_number, "tMBVTagNumber_Array_Attribute_PF");
        modelName=getconfigattrvalue(line._document_number, "modelName_VALB");
        modelDesc=getconfigattrvalue(line._document_number, "modelDescription_VALB");
        businessUnit="TMBV";


        lineNumberValue = line._group_sequence_number;
        parentLineItemNumber = "-1";
        //parentLineItemNumber = line._parent_doc_number + "::" + line._parent_line_item +"##"+_system_selected_document_number;

        lineResStr = lineResStr + linedoc +"~lineNumber_line~" + lineNumberValue + "|"
                        +linedoc + "~tagN_line~" + tagNo+ "|"
                        +linedoc + "~_model_name~" + modelName+ "|"
                        +linedoc + "~modelDescription_line~" + modelDesc+ "|"
                        +linedoc + "~leadTimeInt_line~" + string(leadTime) + "|"
                        +linedoc + "~businessUnit_line~" + businessUnit+ "|"
                        +linedoc + "~mfgBU_line~" + mfgBUValue+ "|"
                        +linedoc + "~alt_line~"+altValue +"|"
                        +linedoc + "~multi_line~"+string(multiValue) +"|"
                        +linedoc + "~parentLineNumber_line~"+parentLineItemNumber+"|";



    }

    ////////////
    //TX3
    ////////////
    if(line._model_variable_name == "durcoTX3") {
        tagNo=getconfigattrvalue(line._document_number, "tagNumberColumn1_PF");
        modelName=getconfigattrvalue(line._document_number, "modelName_TX3");
        modelDesc=getconfigattrvalue(line._document_number, "modelDescription_TX3");
       // modelName = line._model_name;
       // modelDesc =  modelName;
        businessUnit="Durco TX3";

        lineNumberValue = line._group_sequence_number;
        parentLineItemNumber = "-1";
        //parentLineItemNumber = line._parent_doc_number + "::" + line._parent_line_item+"##"+_system_selected_document_number;


        lineResStr = lineResStr + linedoc + "~lineNumber_line~"+ lineNumberValue +"|"
                        +linedoc + "~tagN_line~" + tagNo+ "|"
                        +linedoc + "~_model_name~" + modelName+ "|"
                        +linedoc + "~modelDescription_line~" + modelDesc+ "|"
                        +linedoc + "~leadTimeInt_line~" + string(leadTime) + "|"
                        +linedoc + "~businessUnit_line~" + businessUnit+ "|"
                        +linedoc + "~mfgBU_line~" + mfgBUValue+ "|"
                        +linedoc + "~alt_line~"+altValue +"|"
                        +linedoc + "~multi_line~"+string(multiValue) +"|"
                        +linedoc + "~parentLineNumber_line~"+parentLineItemNumber+"|";

    }


    ///////////////
    // LFPS
    ////////////////


    if(line._model_variable_name == "lFPSActuators_LFPS") {
        tagNo=getconfigattrvalue(line._document_number, "tagNumber_PF");
        //modelName=getconfigattrvalue(line._document_number, "modelName_VALB");
        //modelDesc=getconfigattrvalue(line._document_number, "modelDescription_VALB");
        modelName = line._model_name;
        modelDesc =  modelName;
        businessUnit="LFPS Actuators";

        lineNumberValue = line._group_sequence_number;

        if(selectedValveItem_quote == "") {
          if(line.parentLineNumber_line == "-") { // individual actuator item
            parentLineItemNumber = "-";
          } elif (line.parentLineNumber_line <> "-1" AND line.parentLineNumber_line <> "") { // related actuator item
            parentLineItemNumber = line.parentLineNumber_line;
          } else {
            parentLineItemNumber = "-";
          }
        } else {
          if(line.parentLineNumber_line == "-") { // individual actuator item
            parentLineItemNumber = "-";
          } elif(line.parentLineNumber_line <> "-1" AND line.parentLineNumber_line <> "") {
            parentLineItemNumber = line.parentLineNumber_line;
          } else {
            parentLineItemNumber = selectedValveItem_quote;
          }
        }
        //parentLineItemNumber = line._parent_doc_number + "::" + line._parent_line_item+"##"+_system_selected_document_number;


        lineResStr = lineResStr + linedoc + "~lineNumber_line~"+ lineNumberValue +"|"
                        +linedoc + "~tagN_line~" + tagNo+ "|"
                        +linedoc + "~_model_name~" + modelName+ "|"
                        +linedoc + "~modelDescription_line~" + modelDesc+ "|"
                        +linedoc + "~leadTimeInt_line~" + string(leadTime) + "|"
                        +linedoc + "~businessUnit_line~" + businessUnit+ "|"
                        +linedoc + "~mfgBU_line~" + mfgBUValue+ "|"
                        +linedoc + "~alt_line~"+altValue +"|"
                        +linedoc + "~multi_line~"+string(multiValue) +"|"
                        +linedoc + "~parentLineNumber_line~"+parentLineItemNumber+"|";


    }

    //////////////////
    // argus
    /////////////////
    if(line._model_variable_name == "argusTMBV") {
        tagNo=getconfigattrvalue(line._document_number, "tagNumber_PF");
        //modelName=getconfigattrvalue(line._document_number, "modelName_VALB");
        //modelDesc=getconfigattrvalue(line._document_number, "modelDescription_VALB");
        modelName = line._model_name;
        modelDesc =  modelName;
        businessUnit="Argus TMBV";

        lineNumberValue = line._group_sequence_number;
        parentLineItemNumber = "-1";
        //parentLineItemNumber = line._parent_doc_number + "::" + line._parent_line_item+"##"+_system_selected_document_number;


        lineResStr = lineResStr + linedoc + "~lineNumber_line~"+ lineNumberValue +"|"
                        +linedoc +"~tagN_line~" + tagNo+ "|"
                        +linedoc + "~_model_name~" + modelName+ "|"
                        +linedoc + "~modelDescription_line~" + modelDesc+ "|"
                        +linedoc + "~leadTimeInt_line~" +  string(leadTime) + "|"
                        +linedoc + "~businessUnit_line~" + businessUnit+ "|"
                        +linedoc + "~mfgBU_line~" + mfgBUValue+ "|"
                        +linedoc + "~alt_line~"+altValue +"|"
                        +linedoc + "~multi_line~"+string(multiValue) +"|"
                        +linedoc + "~parentLineNumber_line~"+parentLineItemNumber+"|";


    }




    if(isPart) {
        //if part

        tagNo=getconfigattrvalue(linedoc, "tagNumber_PF");
        // modelName=getconfigattrvalue(linedoc, "_part_display_number");
        modelName = line._part_display_number;
        // modelDesc=getconfigattrvalue(linedoc, "_part_desc");
        modelDesc = line._part_desc;


        tagNo="";
        businessUnit="TMBV";
        altValue = "";

        //print modelName;
        //print modelDesc;
        //lineNumber = line._group_sequence_number;
        lineNumberValue = "P-" + line._group_sequence_number;
        parentLineItemNumber = line._parent_doc_number;
        //parentLineItemNumber = line._parent_doc_number + "::" + line._parent_line_item+"##"+_system_selected_document_number;

        //check for parent key and form the correct spare children string for the same.
        // in the format as chidSpareDocNo+'#S#'+childSpareDocNo2+'#C#'+...
        childrenDocSparePartsString="";
        if(containsKey(parentSpareDocumentMap,line._parent_doc_number)){
            childrenDocSparePartsString = get(parentSpareDocumentMap, line._parent_doc_number);
            if(childrenDocSparePartsString == "") {
                childrenDocSparePartsString = childrenDocSparePartsString + linedoc;
            } else {
                childrenDocSparePartsString = childrenDocSparePartsString+"#S#"+linedoc;
            }
        } else {
            //add first key
            childrenDocSparePartsString = childrenDocSparePartsString + linedoc;
        }
        //print childrenDocSparePartsString;
        put(parentSpareDocumentMap,line._parent_doc_number, childrenDocSparePartsString);
        ct=0;

        if(containsKey(parentSpareDocumentToCountMap,line._parent_doc_number)) {
            spcount = get(parentSpareDocumentToCountMap, line._parent_doc_number);
            if(spcount== "") {
                ct =  ct +1;
            } else {
                ct = atoi(spcount);
                ct = ct + 1;
            }

        } else {
             ct =  ct +1;
        }
        put(parentSpareDocumentToCountMap, line._parent_doc_number, string(ct));

        lineResStr = lineResStr + linedoc + "~lineNumber_line~"+ lineNumberValue +"|"
                        +linedoc + "~tagN_line~" + tagNo+ "|"
                        +linedoc + "~_model_name~" + modelName+ "|"
                        +linedoc + "~modelDescription_line~" + modelDesc+ "|"
                        +linedoc + "~leadTimeInt_line~" + string(leadTime) + "|"
                        +linedoc + "~businessUnit_line~" + businessUnit+ "|"
                        +linedoc + "~parentLineNumber_line~"+parentLineItemNumber+"|";

    }

}

// set the totalCountChildSpareParts_line value with child spare parts doc string

parentKeysArr =  keys(parentSpareDocumentMap);
for key in parentKeysArr {
    partsString = get(parentSpareDocumentMap, key);
    spcount = get(parentSpareDocumentToCountMap,key);
    if(spcount =="") {
    	spcount ="0";
    }
    lineResStr = lineResStr + key +"~childSparePartsDocumentNumbers_line~"+partsString + "|";
    lineResStr = lineResStr + key +"~totalCountChildSpareParts_line~"+ spcount + "|";
    print partsString;
}


return lineResStr;
