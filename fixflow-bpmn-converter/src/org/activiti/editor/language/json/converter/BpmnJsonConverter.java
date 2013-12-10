/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.editor.language.json.converter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import math.geom2d.Point2D;
import math.geom2d.conic.Circle2D;
import math.geom2d.line.Line2D;
import math.geom2d.polygon.Polyline2D;

import org.activiti.editor.constants.EditorJsonConstants;
import org.activiti.editor.constants.StencilConstants;
import org.activiti.editor.language.json.converter.util.JsonConverterUtil;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.bpmn2.Activity;
import org.eclipse.bpmn2.BaseElement;
import org.eclipse.bpmn2.BoundaryEvent;
import org.eclipse.bpmn2.Bpmn2Factory;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.FlowElement;
import org.eclipse.bpmn2.FlowNode;
import org.eclipse.bpmn2.Lane;
import org.eclipse.bpmn2.LaneSet;
import org.eclipse.bpmn2.Participant;
import org.eclipse.bpmn2.SequenceFlow;
import org.eclipse.bpmn2.SubProcess;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.di.BPMNDiagram;
import org.eclipse.bpmn2.di.BPMNEdge;
import org.eclipse.bpmn2.di.BPMNPlane;
import org.eclipse.bpmn2.di.BPMNShape;
import org.eclipse.bpmn2.di.BpmnDiFactory;
import org.eclipse.dd.dc.Bounds;
import org.eclipse.dd.dc.DcFactory;
import org.eclipse.dd.dc.Point;
import org.eclipse.dd.di.DiagramElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.founder.fix.bpmn2extensions.fixflow.DataVariable;
import com.founder.fix.bpmn2extensions.fixflow.Expression;
import com.founder.fix.bpmn2extensions.fixflow.FixFlowFactory;
import com.founder.fix.bpmn2extensions.fixflow.FixFlowPackage;
import com.founder.fix.bpmn2extensions.fixflow.FormUri;
import com.founder.fix.bpmn2extensions.fixflow.TaskSubject;
import com.founder.fix.fixflow.bpmn.converter.FixFlowConverter;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.ProcessDefinitionBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.SequenceFlowBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.SubProcessBehavior;
import com.founder.fix.fixflow.core.impl.bpmn.behavior.TaskSubjectBehavior;
import com.founder.fix.fixflow.core.impl.util.BpmnModelUtil;
import com.founder.fix.fixflow.core.impl.util.EMFExtensionUtil;
import com.founder.fix.fixflow.core.impl.util.StringUtil;

/**
 * @author Tijs Rademakers
 */
public class BpmnJsonConverter implements EditorJsonConstants, StencilConstants, ActivityProcessor {
  
  protected static final Logger LOGGER = LoggerFactory.getLogger(BpmnJsonConverter.class);
  
  protected ObjectMapper objectMapper = new ObjectMapper();
  
  protected static Map<Class<? extends BaseElement>, Class<? extends BaseBpmnJsonConverter>> convertersToJsonMap = 
      new HashMap<Class<? extends BaseElement>, Class<? extends BaseBpmnJsonConverter>>();
  
  protected static Map<String, Class<? extends BaseBpmnJsonConverter>> convertersToBpmnMap = 
      new HashMap<String, Class<? extends BaseBpmnJsonConverter>>();
  
  static {
    
    // start and end events
    StartEventJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    EndEventJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // connectors
    SequenceFlowJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // task types
    BusinessRuleTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    //MailTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    ManualTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    ReceiveTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    ScriptTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    ServiceTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    UserTaskJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    CallActivityJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // gateways
    ExclusiveGatewayJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    InclusiveGatewayJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    ParallelGatewayJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    EventGatewayJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // scope constructs
    SubProcessJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    EventSubProcessJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // catch events
    CatchEventJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // throw events
    ThrowEventJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
    
    // boundary events
    BoundaryEventJsonConverter.fillTypes(convertersToBpmnMap, convertersToJsonMap);
  }
  
  private static final List<String> DI_CIRCLES = new ArrayList<String>();
  private static final List<String> DI_RECTANGLES = new ArrayList<String>();
  private static final List<String> DI_GATEWAY = new ArrayList<String>();
  
  static {
    DI_CIRCLES.add(STENCIL_EVENT_START_ERROR);
    DI_CIRCLES.add(STENCIL_EVENT_START_MESSAGE);
    DI_CIRCLES.add(STENCIL_EVENT_START_NONE);
    DI_CIRCLES.add(STENCIL_EVENT_START_TIMER);
    
    DI_CIRCLES.add(STENCIL_EVENT_BOUNDARY_ERROR);
    DI_CIRCLES.add(STENCIL_EVENT_BOUNDARY_SIGNAL);
    DI_CIRCLES.add(STENCIL_EVENT_BOUNDARY_TIMER);
    
    DI_CIRCLES.add(STENCIL_EVENT_CATCH_MESSAGE);
    DI_CIRCLES.add(STENCIL_EVENT_CATCH_SIGNAL);
    DI_CIRCLES.add(STENCIL_EVENT_CATCH_TIMER);
    
    DI_CIRCLES.add(STENCIL_EVENT_THROW_NONE);
    DI_CIRCLES.add(STENCIL_EVENT_THROW_SIGNAL);
    
    DI_CIRCLES.add(STENCIL_EVENT_END_NONE);
    DI_CIRCLES.add(STENCIL_EVENT_END_ERROR);
    
    DI_RECTANGLES.add(STENCIL_CALL_ACTIVITY);
    DI_RECTANGLES.add(STENCIL_SUB_PROCESS);
    DI_RECTANGLES.add(STENCIL_EVENT_SUB_PROCESS);
    DI_RECTANGLES.add(STENCIL_TASK_BUSINESS_RULE);
    DI_RECTANGLES.add(STENCIL_TASK_MAIL);
    DI_RECTANGLES.add(STENCIL_TASK_MANUAL);
    DI_RECTANGLES.add(STENCIL_TASK_RECEIVE);
    DI_RECTANGLES.add(STENCIL_TASK_SCRIPT);
    DI_RECTANGLES.add(STENCIL_TASK_SERVICE);
    DI_RECTANGLES.add(STENCIL_TASK_USER);
    
    DI_GATEWAY.add(STENCIL_GATEWAY_EVENT);
    DI_GATEWAY.add(STENCIL_GATEWAY_EXCLUSIVE);
    DI_GATEWAY.add(STENCIL_GATEWAY_INCLUSIVE);
    DI_GATEWAY.add(STENCIL_GATEWAY_PARALLEL);
  }

  public ObjectNode convertToJson(Definitions model) {
    ObjectNode modelNode = objectMapper.createObjectNode();
    modelNode.put("bounds", BpmnJsonConverterUtil.createBoundsNode(1485, 1050, 0, 0));
    modelNode.put("resourceId", "canvas");
    
    ObjectNode stencilNode = objectMapper.createObjectNode();
    stencilNode.put("id", "BPMNDiagram");
    modelNode.put("stencil", stencilNode);
    
    ObjectNode stencilsetNode = objectMapper.createObjectNode();
    stencilsetNode.put("namespace", "http://b3mn.org/stencilset/bpmn2.0#");
    stencilsetNode.put("url", "../editor/stencilsets/bpmn2.0/bpmn2.0.json");
    modelNode.put("stencilset", stencilsetNode);
    
    ArrayNode shapesArrayNode = objectMapper.createArrayNode();
    
    ProcessDefinitionBehavior mainProcess = null;
    mainProcess=(ProcessDefinitionBehavior)BpmnModelUtil.getProcess(model);
    EMFExtensionUtil.getDataVariables(mainProcess);
    
    /*
    if (BpmnModelUtil.getProcessList(model).getPools().size() > 0) {
      mainProcess = model.getProcess(model.getPools().get(0).getId());
    } else {
      mainProcess = model.getMainProcess();
    }*/
      
    ObjectNode propertiesNode = objectMapper.createObjectNode();
    if (StringUtils.isNotEmpty(mainProcess.getId())) {
      propertiesNode.put(PROPERTY_OVERRIDE_ID, mainProcess.getId());
    }
    if (StringUtils.isNotEmpty(mainProcess.getName())) {
      propertiesNode.put(PROPERTY_NAME, mainProcess.getName());
    }
    
    
    //fixflow扩展流程属性
    propertiesNode.put(PROPERTY_PROCESS_CATEGORY, mainProcess.getCategory());
    TaskSubjectBehavior taskSubject =  mainProcess.getTaskSubject();
    if(taskSubject!= null){
    	propertiesNode.put(PROPERTY_PROCESS_SUBJECT, mainProcess.getTaskSubject().getExpressionValue());
    }
   
    FormUri formObj = mainProcess.getFormUriObj();
    if(formObj != null){
    	 propertiesNode.put(PROPERTY_PROCESS_DEFAULT_FORMURI,formObj.getExpression().getValue());
    }
    //由于获取流程模型的时候没有loadvariable,所以此处先用emf原始加载的方式加载数据变量，后期可能需要改掉
    List<DataVariable> dataVariables = EMFExtensionUtil.getDataVariables(mainProcess);
    if(dataVariables != null){
    	ObjectNode datavariableNode = objectMapper.createObjectNode();
    	ArrayNode itemsNode = objectMapper.createArrayNode();
    	for(DataVariable dataVariable :dataVariables){
    		ObjectNode datavariableItemNode = objectMapper.createObjectNode();
    		datavariableItemNode.put(PROPERTY_DATAVARIABLE_ID,dataVariable.getId());
    		datavariableItemNode.put(PROPERTY_DATAVARIABLE_TYPE,dataVariable.getDataType());
    		datavariableItemNode.put(PROPERTY_DATAVARIABLE_BIZTYPE,dataVariable.getBizType());
    		datavariableItemNode.put(PROPERTY_DATAVARIABLE_IS_PERSISTENCE,dataVariable.isIsPersistence());
        	Expression expression = dataVariable.getExpression();
        	if(expression != null){
        		datavariableItemNode.put(PROPERTY_DATAVARIABLE_DEFAULT_VALUE,expression.getValue());
        	}
        	itemsNode.add(datavariableItemNode);
        }
    	datavariableNode.put("totalCount", itemsNode.size());
    	datavariableNode.put(EDITOR_PROPERTIES_GENERAL_ITEMS, itemsNode);
        propertiesNode.put(PROPERTY_PROCESS_DATAVARIABLE, datavariableNode);
    }
    
    
    /*
    if (mainProcess.isExecutable() == false) {
      propertiesNode.put(PROPERTY_PROCESS_EXECUTABLE, PROPERTY_VALUE_NO);
    }*/
    
    propertiesNode.put(PROPERTY_PROCESS_NAMESPACE, model.getTargetNamespace());
    
    if (StringUtils.isNotEmpty(BpmnModelUtil.getDocumentation(mainProcess))) {
      propertiesNode.put(PROPERTY_DOCUMENTATION, BpmnModelUtil.getDocumentation(mainProcess));
    }
    modelNode.put(EDITOR_SHAPE_PROPERTIES, propertiesNode);
    
    
    /*****lane的处理******/
    List<Lane> lanes=new ArrayList<Lane>();
    List<FlowElement> laneFlowElements = new ArrayList<FlowElement>();
    //ArrayNode laneShapesArrayNode = objectMapper.createArrayNode();
    fillAllLanes(lanes, mainProcess);
    if(lanes.size() >0){
      for (Lane lane : lanes) {
    	  addLaneElements(lane,mainProcess,laneFlowElements,model,shapesArrayNode,0.0,0.0);
      }
    }
   
     processFlowElements(BpmnModelUtil.getProcess(model).getFlowElements(), model, shapesArrayNode, 0.0, 0.0,laneFlowElements);
    
    
    modelNode.put(EDITOR_CHILD_SHAPES, shapesArrayNode);
    return modelNode;
  }
  
  public void addLaneElements(Lane lane,Process mainProcess,List<FlowElement> laneFlowElements,Definitions model,ArrayNode shapesArrayNode,double x,double y){
	  Bounds laneGraphicInfo =BpmnModelUtil.getBpmnShape(model, lane.getId()).getBounds();
      ObjectNode laneNode = BpmnJsonConverterUtil.createChildShape(lane.getId(), STENCIL_LANE, 
          laneGraphicInfo.getX()-x + laneGraphicInfo.getWidth(), laneGraphicInfo.getY()-y + laneGraphicInfo.getHeight(), 
          laneGraphicInfo.getX()-x, laneGraphicInfo.getY()-y);
      ObjectNode lanePropertiesNode = objectMapper.createObjectNode();
      lanePropertiesNode.put("parentpool", "");
      lanePropertiesNode.put("parentlane", "");
      lanePropertiesNode.put("showcaption", "true");
      lanePropertiesNode.put("bgcolor", "");
      lanePropertiesNode.put("bordercolor", "#000000");
      
      laneNode.put(EDITOR_SHAPE_PROPERTIES, lanePropertiesNode);
      ArrayNode dockArrayNode = objectMapper.createArrayNode();
      laneNode.put("dockers", dockArrayNode);
      laneNode.put("outgoing", dockArrayNode);
      shapesArrayNode.add(laneNode);
      ArrayNode elementShapesArrayNode = objectMapper.createArrayNode();
      laneNode.put(EDITOR_CHILD_SHAPES, elementShapesArrayNode);
      
      for (FlowElement flowElement : mainProcess.getFlowElements()) {
        if (lane.getFlowNodeRefs().contains(flowElement)) {
          Class<? extends BaseBpmnJsonConverter> converter = convertersToJsonMap.get(flowElement.getClass());
          if (converter != null) {
            try {
              converter.newInstance().convertToJson(flowElement, this, model, elementShapesArrayNode, 
                  laneGraphicInfo.getX(), laneGraphicInfo.getY());
              laneFlowElements.add(flowElement);
            } catch (Exception e) {
              LOGGER.error("Error converting {}", flowElement, e);
            }
          }
        }
      }
      
      if(lane.getChildLaneSet() != null){
    	  for(Lane tmpLane :lane.getChildLaneSet().getLanes()){
    		  addLaneElements(tmpLane,mainProcess,laneFlowElements,model,elementShapesArrayNode,laneGraphicInfo.getX(),laneGraphicInfo.getY());
    	  }
      }
  }
  
  public void processFlowElements(Collection<FlowElement> flowElements, Definitions model, ArrayNode shapesArrayNode, 
      double subProcessX, double subProcessY,List<FlowElement> laneFlowElements) {
    
    for (FlowElement flowElement : flowElements) {
    	if(laneFlowElements !=null && laneFlowElements.contains(flowElement)){
    		continue;
    	}
    	Class<? extends BaseBpmnJsonConverter> converter = convertersToJsonMap.get(flowElement.getClass());
    	if (converter != null) {
    		try {
    			converter.newInstance().convertToJson(flowElement, this, model, shapesArrayNode, 
    					subProcessX, subProcessY);
    		} catch (Exception e) {
    			LOGGER.error("Error converting {}", flowElement, e);
    			e.printStackTrace();
    		}
    	}
    }
  }
  
  private void fillAllLanes(List<Lane> lanes,BaseElement baseElement){
	  if(baseElement instanceof Process){
		  Process process = (Process)baseElement;
		  for(LaneSet laneSet :process.getLaneSets()){
			  for(Lane lane : laneSet.getLanes()){
//				  fillAllLanes(lanes,lane);
				  lanes.add(lane);
			  }
		  }
	  }
//	  if(baseElement instanceof Lane){
//		  Lane lane = (Lane) baseElement;
//		  lanes.add(lane);
//		  LaneSet laneSet = lane.getChildLaneSet();
//		  if(laneSet != null){
//			  for(Lane tmpLane : laneSet.getLanes()){
//				  fillAllLanes(lanes,tmpLane);
//			  } 
//		  }
//	  }
  }
  
  public Definitions convertToBpmnModel(JsonNode modelNode) {
	  //加载一个空的definitions
	  
	  InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("org/activiti/editor/language/node_template.bpmn");
	  Definitions bpmnModel = new FixFlowConverter().getDefinitions("temp", inputStream);
	  BPMNDiagram bpmnDiagram=bpmnModel.getDiagrams().get(0);
	  BPMNPlane bpmnPlane = bpmnDiagram.getPlane();
	  Process process = (Process)bpmnModel.getRootElements().get(0);
	  Map<String, JsonNode> shapeMap = new HashMap<String, JsonNode> ();
	  Map<String, JsonNode> sourceRefMap = new HashMap<String, JsonNode>();
	  Map<String, JsonNode> edgeMap = new HashMap<String, JsonNode>();
	  Map<String, List<JsonNode>> sourceAndTargetMap = new HashMap<String, List<JsonNode>>();
	  readShapeDI(modelNode, 0, 0, shapeMap, sourceRefMap, bpmnModel);
	  filterAllEdges(modelNode, edgeMap, sourceAndTargetMap, shapeMap, sourceRefMap);
	  ArrayNode shapesArrayNode = (ArrayNode) modelNode.get(EDITOR_CHILD_SHAPES);
	  boolean nonEmptyPoolFound = false;
	  // first create the pool structure

//	  for (JsonNode shapeNode : shapesArrayNode) {
//		  String stencilId = BpmnJsonConverterUtil.getStencilId(shapeNode);
//		  if (STENCIL_LANE.equals(stencilId)) {
//			  Lane lane = Bpmn2Factory.eINSTANCE.createLane();// Lane();
//			  if(process.getLaneSets().size() >0){
//				  process.getLaneSets().get(0).getLanes().add(lane);
//			  }else{
//				  LaneSet laneSet = Bpmn2Factory.eINSTANCE.createLaneSet();
//				  laneSet.getLanes().add(lane);
//				  process.getLaneSets().add(laneSet);
//			  }
//			  processJsonElements(shapeNode.get(EDITOR_CHILD_SHAPES), modelNode, lane, shapeMap,sourceAndTargetMap,bpmnModel);
//		  }
//	  }
	  
	  if (nonEmptyPoolFound == false) {
	      JsonNode processIdNode = JsonConverterUtil.getProperty(PROPERTY_PROCESS_ID, modelNode);
	      if(processIdNode != null && StringUtils.isNotEmpty(processIdNode.asText())) {
	    	  process.setId(processIdNode.asText());
	      }
	      JsonNode processNameNode = JsonConverterUtil.getProperty(PROPERTY_NAME, modelNode);
	      if(processNameNode != null && StringUtils.isNotEmpty(processNameNode.asText())) {
	    	  process.setName(processNameNode.asText());
	      }
	      
	      //加载process的扩展元素
	      //任务主题
	      JsonNode processSubject = JsonConverterUtil.getProperty(PROPERTY_PROCESS_SUBJECT, modelNode);
	      if(processSubject != null && StringUtils.isNotEmpty(processSubject.asText())) {
	    	  TaskSubject taskSubject = FixFlowFactory.eINSTANCE.createTaskSubject();
	          Expression subjectExpression = FixFlowFactory.eINSTANCE.createExpression();
	          subjectExpression.setName(processSubject.asText());
	          subjectExpression.setValue(processSubject.asText());
	          taskSubject.setExpression(subjectExpression);
	          taskSubject.setId("TaskSubject_1");
	          BpmnModelUtil.addExtensionElement(process, FixFlowPackage.Literals.DOCUMENT_ROOT__TASK_SUBJECT, taskSubject);
	      }
	      
	      //流程分类
	      JsonNode processCategory = JsonConverterUtil.getProperty(PROPERTY_PROCESS_CATEGORY, modelNode);
	      if(processCategory != null && StringUtil.isNotEmpty(processCategory.asText())){
	    	  BpmnModelUtil.addExtensionAttribute(process, FixFlowPackage.Literals.DOCUMENT_ROOT__CATEGORY, processCategory.asText());
	      }
	      
	      //默认表单
	      JsonNode processFormUri = JsonConverterUtil.getProperty(PROPERTY_PROCESS_DEFAULT_FORMURI, modelNode);
	      if(processFormUri != null && StringUtil.isNotEmpty(processFormUri.asText())){
	    	  FormUri formUri = FixFlowFactory.eINSTANCE.createFormUri();
	    	  Expression expression = FixFlowFactory.eINSTANCE.createExpression();
	    	  expression.setName(processFormUri.asText());
	    	  expression.setValue(processFormUri.asText());
	    	  formUri.setExpression(expression);
	    	  BpmnModelUtil.addExtensionElement(process, FixFlowPackage.Literals.DOCUMENT_ROOT__FORM_URI, formUri);
	      }
	      
	      //流程变量
	      JsonNode dataVariablesNode = JsonConverterUtil.getProperty(PROPERTY_PROCESS_DATAVARIABLE, modelNode);
	      if(dataVariablesNode != null){
	    	  JsonNode itemsNode = dataVariablesNode.get(EDITOR_PROPERTIES_GENERAL_ITEMS);
	    	  if(itemsNode != null) {
	    	        Iterator<JsonNode> variableIterator = itemsNode.getElements();
	    	        while(variableIterator.hasNext()) {
	    	        	JsonNode variableNode = variableIterator.next();
	    	        	String id = variableNode.get(PROPERTY_DATAVARIABLE_ID).asText();
	    	        	String dataType = variableNode.get(PROPERTY_DATAVARIABLE_TYPE).asText();
	    	        	String bizType = variableNode.get(PROPERTY_DATAVARIABLE_BIZTYPE).asText();
	    	        	boolean isPersistence = StringUtil.getBoolean(variableNode.get(PROPERTY_DATAVARIABLE_IS_PERSISTENCE));
	    	        	String expression = "";
	    	        	JsonNode tmpNode = variableNode.get(PROPERTY_DATAVARIABLE_DEFAULT_VALUE);
	    	        	if(tmpNode != null){
	    	        		expression = tmpNode.asText();
	    	        	}
	    	        	DataVariable dataVariableObj = FixFlowFactory.eINSTANCE.createDataVariable();
	    	        	dataVariableObj.setBizType(bizType);
	    	        	dataVariableObj.setId(id);
	    	        	dataVariableObj.setDataType(dataType);
	    	        	dataVariableObj.setIsPersistence(isPersistence);
	    	        	Expression variableExpression = FixFlowFactory.eINSTANCE.createExpression();
	    	        	variableExpression.setValue(expression);
	    	        	variableExpression.setName(expression);
	    	        	dataVariableObj.setExpression(variableExpression);
	    	        	BpmnModelUtil.addExtensionElement(process, FixFlowPackage.Literals.DOCUMENT_ROOT__DATA_VARIABLE, dataVariableObj);
	    	        }
	    	  }
	      }
	      
	      JsonNode processTargetNamespace = JsonConverterUtil.getProperty(PROPERTY_PROCESS_NAMESPACE, modelNode);
	      if(processTargetNamespace != null && StringUtils.isNotEmpty(processTargetNamespace.asText())) {
	    	  bpmnModel.setTargetNamespace(processTargetNamespace.asText());
	      }
	      processJsonElements(shapesArrayNode, modelNode, process, shapeMap,sourceAndTargetMap,bpmnModel);
	  }
    
	  	// Add flows to map for later processing
	  
	  Map<String, SequenceFlow> flowSourceMap = new HashMap<String, SequenceFlow>();
	  Map<String, SequenceFlow> flowTargetMap = new HashMap<String, SequenceFlow>();
	  addAllSequenceFlows(process.getFlowElements(), flowSourceMap, flowTargetMap);
	  postProcessElements(process, process.getFlowElements(), flowSourceMap, flowTargetMap);
	  bpmnPlane.setBpmnElement(BpmnModelUtil.getBaseElement(bpmnModel, process.getId()));
	  for(DiagramElement diagram :  bpmnModel.getDiagrams().get(0).getPlane().getPlaneElement()){
		  if(diagram instanceof BPMNShape){
			  BPMNShape bpmnShape = (BPMNShape)diagram;
			  String shapeId = bpmnShape.getId();
			  String elementId = BpmnJsonConverterUtil.getElementIdFromShapeId(shapeId);
			  BaseElement bpmnElement = BpmnModelUtil.getElement(bpmnModel,elementId,BaseElement.class);
			  bpmnShape.setBpmnElement(bpmnElement);
		  }
	  }
	  readEdgeDI(edgeMap, sourceAndTargetMap, bpmnModel);
	  return bpmnModel;
  }
  
  public void processJsonElements(JsonNode shapesArrayNode, JsonNode modelNode, 
      BaseElement parentElement, Map<String, JsonNode> shapeMap,Map<String, List<JsonNode>> sourceAndTargetMap,Definitions model) {
    
    for (JsonNode shapeNode : shapesArrayNode) {
    	String stencilId = BpmnJsonConverterUtil.getStencilId(shapeNode);
    	if(STENCIL_LANE.equals(stencilId)){
    		Lane lane = Bpmn2Factory.eINSTANCE.createLane();
    		String override = BpmnJsonConverterUtil.getElementId(shapeNode);
    		lane.setId(override);
    		lane.setName("aaaa");
    		if(parentElement instanceof Lane){
    			Lane parentLane = ((Lane)parentElement);
    			if(parentLane.getChildLaneSet() == null){
    				LaneSet laneSet = Bpmn2Factory.eINSTANCE.createLaneSet();
  			  		laneSet.getLanes().add(lane);
  			  		parentLane.setChildLaneSet(laneSet);
    			}else{
    				parentLane.getChildLaneSet().getLanes().add(lane);
    			}
    		}else if(parentElement instanceof Process){
    			Process process = (Process)parentElement;
    			if(process.getLaneSets().size() >0){
    				process.getLaneSets().get(0).getLanes().add(lane);
  			  	}else{
  			  		LaneSet laneSet = Bpmn2Factory.eINSTANCE.createLaneSet();
  			  		laneSet.getLanes().add(lane);
  			  		process.getLaneSets().add(laneSet);
  			  	}
    		}
			processJsonElements(shapeNode.get(EDITOR_CHILD_SHAPES), modelNode, lane, shapeMap,sourceAndTargetMap,model);
			continue;
    	}
    	
      Class<? extends BaseBpmnJsonConverter> converter = convertersToBpmnMap.get(stencilId);
      if (converter != null) {
        try {
          converter.newInstance().convertToBpmnModel(shapeNode, modelNode, this, parentElement, shapeMap,sourceAndTargetMap,model);
        } catch (Exception e) {
        	e.printStackTrace();
          LOGGER.error("Error converting {}", BpmnJsonConverterUtil.getStencilId(shapeNode), e);
        }
      }
    }
    
    //fixflow线条处理
    if (parentElement instanceof Process) {
        ProcessDefinitionBehavior process = (ProcessDefinitionBehavior) parentElement;
        addSourceAndTarget(process.getFlowElements(),sourceAndTargetMap,model);
    }
  }
  
  //给线条增加source和target元素
  private void addSourceAndTarget(Collection<FlowElement> flowElements,Map<String, List<JsonNode>> sourceAndTargetMap,Definitions model){
	  for(FlowElement flowElement:flowElements){
      	if(flowElement instanceof SequenceFlowBehavior){
      		List<JsonNode> sourceAndTarget = sourceAndTargetMap.get(BpmnJsonConverterUtil.getFormatEdgeId(flowElement.getId()));
      		String sourceRef = BpmnJsonConverterUtil.getElementId(sourceAndTarget.get(0));
      		String target = BpmnJsonConverterUtil.getElementId(sourceAndTarget.get(1));
      		FlowElement sourceRefNode = BpmnModelUtil.getElement(model, sourceRef, FlowNode.class);
      		FlowElement targetNode = BpmnModelUtil.getElement(model, target, FlowNode.class);
      		((SequenceFlowBehavior)flowElement).setSourceRef((FlowNode)sourceRefNode);
      		((SequenceFlowBehavior)flowElement).setTargetRef((FlowNode)targetNode);
      	}
      	if(flowElement instanceof SubProcessBehavior){
      		SubProcessBehavior subProcessBehavior = (SubProcessBehavior)flowElement;
      		addSourceAndTarget(subProcessBehavior.getFlowElements(),sourceAndTargetMap,model);
      	}
      }
  }
  
  private void fillSubShapes(Map<String, SubProcess> subShapesMap, SubProcess subProcess) {
    for (FlowElement flowElement : subProcess.getFlowElements()) {
      if (flowElement instanceof SubProcess) {
        SubProcess childSubProcess = (SubProcess) flowElement;
        fillSubShapes(subShapesMap, childSubProcess);
      } else {
        subShapesMap.put(flowElement.getId(), subProcess);
      }
    }
  }
  
  private void addAllSequenceFlows(Collection<FlowElement> flowElementList,
      Map<String, SequenceFlow> flowSourceMap, Map<String, SequenceFlow> flowTargetMap) {
    
    for (FlowElement flowElement : flowElementList) {
      if (flowElement instanceof SequenceFlow) {
        SequenceFlow flow = (SequenceFlow) flowElement;
        flowSourceMap.put(flow.getSourceRef().getId(), flow);
        flowTargetMap.put(flow.getTargetRef().getId(), flow);
      } else if (flowElement instanceof SubProcess) {
        SubProcess subProcess = (SubProcess) flowElement;
        addAllSequenceFlows(subProcess.getFlowElements(), flowSourceMap, flowTargetMap);
      }
    }
  }
  
  private void postProcessElements(Process process, Collection<FlowElement> flowElementList,
      Map<String, SequenceFlow> flowSourceMap, Map<String, SequenceFlow> flowTargetMap) {
    
    for (FlowElement flowElement : flowElementList) {
      
      if (flowElement instanceof FlowNode) {
        if (flowSourceMap.containsKey(flowElement.getId())) {
          ((FlowNode) flowElement).getOutgoing().add(flowSourceMap.get(flowElement.getId()));
        }
        if (flowTargetMap.containsKey(flowElement.getId())) {
          ((FlowNode) flowElement).getIncoming().add(flowTargetMap.get(flowElement.getId()));
        }
      }
      
      if (flowElement instanceof BoundaryEvent) {
        BoundaryEvent boundaryEvent = (BoundaryEvent) flowElement;
        Activity activity = retrieveAttachedRefObject(boundaryEvent.getAttachedToRef().getId(), process.getFlowElements());
        
        if (activity == null) {
          LOGGER.warn("Boundary event " + boundaryEvent.getId() + " is not attached to any activity");
        } else {
          boundaryEvent.setAttachedToRef(activity);
          activity.getBoundaryEventRefs().add(boundaryEvent);
        }
      } else if (flowElement instanceof SubProcess) {
        SubProcess subProcess = (SubProcess) flowElement;
        postProcessElements(process, subProcess.getFlowElements(), flowSourceMap, flowTargetMap);
      }
    }
  }
   
  private Activity retrieveAttachedRefObject(String attachedToRefId, Collection<FlowElement> flowElementList) {
    for (FlowElement flowElement : flowElementList) {
      if (attachedToRefId.equals(flowElement.getId())) {
        return (Activity) flowElement;
      } else if (flowElement instanceof SubProcess) {
        SubProcess subProcess = (SubProcess) flowElement;
        Activity activity = retrieveAttachedRefObject(attachedToRefId, subProcess.getFlowElements());
        if (activity != null) {
          return activity;
        }
      }
    }
    return null;
  }
  
  private void readShapeDI(JsonNode objectNode, double parentX, double parentY, 
      Map<String, JsonNode> shapeMap, Map<String, JsonNode> sourceRefMap, Definitions bpmnModel) {
    
    if (objectNode.get(EDITOR_CHILD_SHAPES) != null) {
      for (JsonNode jsonChildNode : objectNode.get(EDITOR_CHILD_SHAPES)) {
        
        String stencilId = BpmnJsonConverterUtil.getStencilId(jsonChildNode);
        if (STENCIL_SEQUENCE_FLOW.equals(stencilId) == false) {
          
        	String elementId = BpmnJsonConverterUtil.getElementId(jsonChildNode);
	    	Bounds graphicInfo=DcFactory.eINSTANCE.createBounds();
	    	BPMNShape bpmnShape=BpmnDiFactory.eINSTANCE.createBPMNShape();
	    	bpmnShape.setBounds(graphicInfo);
	    	bpmnShape.setId(BpmnJsonConverterUtil.getFormatShapeId(elementId));
          JsonNode boundsNode = jsonChildNode.get(EDITOR_BOUNDS);
          ObjectNode upperLeftNode = (ObjectNode) boundsNode.get(EDITOR_BOUNDS_UPPER_LEFT);
          graphicInfo.setX((float)(upperLeftNode.get(EDITOR_BOUNDS_X).asDouble() + parentX));
          graphicInfo.setY((float)(upperLeftNode.get(EDITOR_BOUNDS_Y).asDouble() + parentY));
          
          ObjectNode lowerRightNode = (ObjectNode) boundsNode.get(EDITOR_BOUNDS_LOWER_RIGHT);
          graphicInfo.setWidth((float)(lowerRightNode.get(EDITOR_BOUNDS_X).asDouble() - graphicInfo.getX() + parentX));
          graphicInfo.setHeight((float)(lowerRightNode.get(EDITOR_BOUNDS_Y).asDouble() - graphicInfo.getY() + parentY));
          
          String childShapeId = jsonChildNode.get(EDITOR_SHAPE_ID).asText();
          
          //bpmnShape.setBpmnElement(BpmnModelUtil.getBaseElement(bpmnModel, BpmnJsonConverterUtil.getElementId(jsonChildNode)));
          //这里注释掉的以后需要实现 bpmnModel.addGraphicInfo(BpmnJsonConverterUtil.getElementId(jsonChildNode), graphicInfo);
          bpmnModel.getDiagrams().get(0).getPlane().getPlaneElement().add(bpmnShape);
          shapeMap.put(childShapeId, jsonChildNode);
          
          ArrayNode outgoingNode = (ArrayNode) jsonChildNode.get("outgoing");
          if (outgoingNode != null && outgoingNode.size() > 0) {
            for (JsonNode outgoingChildNode : outgoingNode) {
              JsonNode resourceNode = outgoingChildNode.get(EDITOR_SHAPE_ID);
              if (resourceNode != null) {
                sourceRefMap.put(resourceNode.asText(), jsonChildNode);
              }
            }
          }
          
          readShapeDI(jsonChildNode, graphicInfo.getX(), graphicInfo.getY(), shapeMap, sourceRefMap, bpmnModel);
        }
      }
    }
  }
  
  private void filterAllEdges(JsonNode objectNode, 
      Map<String, JsonNode> edgeMap, Map<String, List<JsonNode>> sourceAndTargetMap,
      Map<String, JsonNode> shapeMap, Map<String, JsonNode> sourceRefMap) {
    
    if (objectNode.get(EDITOR_CHILD_SHAPES) != null) {
      for (JsonNode jsonChildNode : objectNode.get(EDITOR_CHILD_SHAPES)) {
        
        ObjectNode childNode = (ObjectNode) jsonChildNode;
        String stencilId = BpmnJsonConverterUtil.getStencilId(childNode);
        if (STENCIL_SUB_PROCESS.equals(stencilId) || STENCIL_LANE.equals(stencilId)) {
          filterAllEdges(childNode, edgeMap, sourceAndTargetMap, shapeMap, sourceRefMap);
          
        } else if (STENCIL_SEQUENCE_FLOW.equals(stencilId)) {
          
          String childEdgeId = BpmnJsonConverterUtil.getElementId(childNode);
          String targetRefId = childNode.get("target").get(EDITOR_SHAPE_ID).asText();
          List<JsonNode> sourceAndTargetList = new ArrayList<JsonNode>();
          sourceAndTargetList.add(sourceRefMap.get(childNode.get(EDITOR_SHAPE_ID).asText()));
          sourceAndTargetList.add(shapeMap.get(targetRefId));
          
          edgeMap.put(BpmnJsonConverterUtil.getFormatEdgeId(childEdgeId), childNode);
          sourceAndTargetMap.put(BpmnJsonConverterUtil.getFormatEdgeId(childEdgeId), sourceAndTargetList);
        }
      }
    }
  }
  
  private void readEdgeDI(Map<String, JsonNode> edgeMap, Map<String, List<JsonNode>> sourceAndTargetMap, Definitions bpmnModel) {
    for (String edgeId : edgeMap.keySet()) {
      
      JsonNode edgeNode = edgeMap.get(edgeId);
      List<JsonNode> sourceAndTargetList = sourceAndTargetMap.get(edgeId);
      
      JsonNode sourceRefNode = sourceAndTargetList.get(0);
      JsonNode targetRefNode = sourceAndTargetList.get(1);
      
      if (sourceRefNode == null) {
      	LOGGER.info("Skipping edge {} because source ref is null", edgeId);
      	continue;
      }
      
      if (targetRefNode == null) {
      	LOGGER.info("Skipping edge {} because target ref is null", edgeId);
      	continue;
      }
      
      JsonNode dockersNode = edgeNode.get(EDITOR_DOCKERS);
      double sourceDockersX = dockersNode.get(0).get(EDITOR_BOUNDS_X).getDoubleValue();
      double sourceDockersY = dockersNode.get(0).get(EDITOR_BOUNDS_Y).getDoubleValue();
      
      Bounds sourceInfo = BpmnModelUtil.getBpmnShape(bpmnModel, BpmnJsonConverterUtil.getElementId(sourceRefNode)).getBounds();
      Bounds targetInfo = BpmnModelUtil.getBpmnShape(bpmnModel, BpmnJsonConverterUtil.getElementId(targetRefNode)).getBounds();
      
      /*JsonNode sourceRefBoundsNode = sourceRefNode.get(EDITOR_BOUNDS);
      BoundsLocation sourceRefUpperLeftLocation = getLocation(EDITOR_BOUNDS_UPPER_LEFT, sourceRefBoundsNode);
      BoundsLocation sourceRefLowerRightLocation = getLocation(EDITOR_BOUNDS_LOWER_RIGHT, sourceRefBoundsNode);
      
      JsonNode targetRefBoundsNode = targetRefNode.get(EDITOR_BOUNDS);
      BoundsLocation targetRefUpperLeftLocation = getLocation(EDITOR_BOUNDS_UPPER_LEFT, targetRefBoundsNode);
      BoundsLocation targetRefLowerRightLocation = getLocation(EDITOR_BOUNDS_LOWER_RIGHT, targetRefBoundsNode);*/
      
      double sourceRefLineX = sourceInfo.getX() + sourceDockersX;
      double sourceRefLineY = sourceInfo.getY() + sourceDockersY;
      
      double nextPointInLineX;
      double nextPointInLineY;
      
      nextPointInLineX = dockersNode.get(1).get(EDITOR_BOUNDS_X).getDoubleValue();
      nextPointInLineY = dockersNode.get(1).get(EDITOR_BOUNDS_Y).getDoubleValue();
      if (dockersNode.size() == 2) {
        nextPointInLineX += targetInfo.getX();
        nextPointInLineY += targetInfo.getY();
      }
      
      Line2D firstLine = new Line2D(sourceRefLineX, sourceRefLineY, nextPointInLineX, nextPointInLineY);
      
      String sourceRefStencilId = BpmnJsonConverterUtil.getStencilId(sourceRefNode);
      String targetRefStencilId = BpmnJsonConverterUtil.getStencilId(targetRefNode);
      
      
      
      List<Point> graphicInfoList = new ArrayList<Point>();
      
      if (DI_CIRCLES.contains(sourceRefStencilId)) {
        Circle2D eventCircle = new Circle2D(sourceInfo.getX() + sourceDockersX, 
            sourceInfo.getY() + sourceDockersY, sourceDockersX);
        
        Collection<Point2D> intersections = eventCircle.intersections(firstLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
      
      } else if (DI_RECTANGLES.contains(sourceRefStencilId)) {
        Polyline2D rectangle = createRectangle(sourceInfo);
        
        Collection<Point2D> intersections = rectangle.intersections(firstLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
      
      } else if (DI_GATEWAY.contains(sourceRefStencilId)) {
        Polyline2D gatewayRectangle = createGateway(sourceInfo);
        
        Collection<Point2D> intersections = gatewayRectangle.intersections(firstLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
      }
      
      Line2D lastLine = null;
      
      if (dockersNode.size() > 2) {
        for(int i = 1; i < dockersNode.size() - 1; i++) {
          double x = dockersNode.get(i).get(EDITOR_BOUNDS_X).getDoubleValue();
          double y = dockersNode.get(i).get(EDITOR_BOUNDS_Y).getDoubleValue();
          graphicInfoList.add(createGraphicInfo(x, y));
        }
        
        double startLastLineX = dockersNode.get(dockersNode.size() - 2).get(EDITOR_BOUNDS_X).getDoubleValue();
        double startLastLineY = dockersNode.get(dockersNode.size() - 2).get(EDITOR_BOUNDS_Y).getDoubleValue();
        
        double endLastLineX = dockersNode.get(dockersNode.size() - 1).get(EDITOR_BOUNDS_X).getDoubleValue();
        double endLastLineY = dockersNode.get(dockersNode.size() - 1).get(EDITOR_BOUNDS_Y).getDoubleValue();
        
        endLastLineX += targetInfo.getX();
        endLastLineY += targetInfo.getY();
        
        lastLine = new Line2D(startLastLineX, startLastLineY, endLastLineX, endLastLineY);
        
      } else {
        lastLine = firstLine;
      }
      
      if (DI_RECTANGLES.contains(targetRefStencilId)) {
        Polyline2D rectangle = createRectangle(targetInfo);
        
        Collection<Point2D> intersections = rectangle.intersections(lastLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
        
      } else if (DI_CIRCLES.contains(targetRefStencilId)) {
        
        double targetDockersX = dockersNode.get(dockersNode.size() - 1).get(EDITOR_BOUNDS_X).getDoubleValue();
        double targetDockersY = dockersNode.get(dockersNode.size() - 1).get(EDITOR_BOUNDS_Y).getDoubleValue();
        
        Circle2D eventCircle = new Circle2D(targetInfo.getX() + targetDockersX, 
            targetInfo.getY() + targetDockersY, targetDockersX);
        
        Collection<Point2D> intersections = eventCircle.intersections(lastLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
        
      } else if (DI_GATEWAY.contains(targetRefStencilId)) {
        Polyline2D gatewayRectangle = createGateway(targetInfo);
        
        Collection<Point2D> intersections = gatewayRectangle.intersections(lastLine);
        Point2D intersection = intersections.iterator().next();
        graphicInfoList.add(createGraphicInfo(intersection.getX(), intersection.getY()));
      }
      
      BPMNEdge eggeTemp=BpmnDiFactory.eINSTANCE.createBPMNEdge();
      eggeTemp.getWaypoint().addAll(graphicInfoList);
      eggeTemp.setId(edgeId);
      BPMNShape sourceElement = BpmnModelUtil.getBpmnShape(bpmnModel, BpmnJsonConverterUtil.getElementId(sourceRefNode));
      BPMNShape targetElement = BpmnModelUtil.getBpmnShape(bpmnModel, BpmnJsonConverterUtil.getElementId(targetRefNode));
      eggeTemp.setSourceElement(sourceElement);
      eggeTemp.setTargetElement(targetElement);
      eggeTemp.setBpmnElement(BpmnModelUtil.getElement(bpmnModel,BpmnJsonConverterUtil.getElementIdFromEdgeId(edgeId),BaseElement.class));
      bpmnModel.getDiagrams().get(0).getPlane().getPlaneElement().add(eggeTemp);
    }
  }
  
  private Polyline2D createRectangle(Bounds graphicInfo) {
    Polyline2D rectangle = new Polyline2D(new Point2D(graphicInfo.getX(), graphicInfo.getY()),
        new Point2D(graphicInfo.getX() + graphicInfo.getWidth(), graphicInfo.getY()),
        new Point2D(graphicInfo.getX() + graphicInfo.getWidth(), graphicInfo.getY() + graphicInfo.getHeight()),
        new Point2D(graphicInfo.getX(), graphicInfo.getY() + graphicInfo.getHeight()),
        new Point2D(graphicInfo.getX(), graphicInfo.getY()));
    return rectangle;
  }
  
  private Polyline2D createGateway(Bounds graphicInfo) {
    
    double middleX = graphicInfo.getX() + (graphicInfo.getWidth() / 2);
    double middleY = graphicInfo.getY() + (graphicInfo.getHeight() / 2);
    
    Polyline2D gatewayRectangle = new Polyline2D(new Point2D(graphicInfo.getX(), middleY),
        new Point2D(middleX, graphicInfo.getY()),
        new Point2D(graphicInfo.getX() + graphicInfo.getWidth(), middleY),
        new Point2D(middleX, graphicInfo.getY() + graphicInfo.getHeight()),
        new Point2D(graphicInfo.getX(), middleY));
    
    return gatewayRectangle;
  }
  
  private Point createGraphicInfo(double x, double y) {
	  Point graphicInfo =DcFactory.eINSTANCE.createPoint();
    graphicInfo.setX((float)x);
    graphicInfo.setY((float)y);
    return graphicInfo;
  }
}
