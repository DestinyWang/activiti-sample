package org.destiny.activiti.cmd;

import lombok.AllArgsConstructor;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.engine.ActivitiEngineAgenda;
import org.activiti.engine.impl.history.HistoryManager;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ExecutionEntityManager;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManager;
import org.activiti.engine.impl.util.ProcessDefinitionUtil;

import java.util.List;
import java.util.Map;

/**
 * @author destiny
 * destinywk@163.com
 * ------------------------------------------------------------------
 * <p></p>
 * ------------------------------------------------------------------
 * design by 2018/12/22 15:04
 * @version 1.8
 * @since JDK 1.8.0_101
 */
@AllArgsConstructor
public class SequenceFlowSourceJumpCmd implements Command<Void> {

    private String taskId;
    private String targetNodeId;
    private Map<String, Object> condition;

    @Override
    public Void execute(CommandContext commandContext) {
        ActivitiEngineAgenda agenda = commandContext.getAgenda();
        TaskEntityManager taskEntityManager = commandContext.getTaskEntityManager();
        TaskEntity taskEntity = taskEntityManager.findById(taskId);
        // 执行实例 id
        String executionId = taskEntity.getExecutionId();
        String processDefinitionId = taskEntity.getProcessDefinitionId();
        ExecutionEntityManager executionEntityManager = commandContext.getExecutionEntityManager();
        HistoryManager historyManager = commandContext.getHistoryManager();
        // 执行实例对象
        ExecutionEntity executionEntity = executionEntityManager.findById(executionId);
        Process process = ProcessDefinitionUtil.getProcess(processDefinitionId);
        FlowElement flowElement = process.getFlowElement(targetNodeId);
        if (flowElement == null) {
            throw new RuntimeException("目标节点不存在");
        }
        SequenceFlow sequenceFlow = null;
        if (flowElement instanceof FlowNode) {
            FlowNode flowNode = (FlowNode) flowElement;
            // 找到所有的入线, 并取其中唯一的一条
            List<SequenceFlow> incomingFlows = flowNode.getIncomingFlows();
            sequenceFlow = incomingFlows.get(0);
        }
        if (sequenceFlow == null) {
            throw new RuntimeException("目标连线不存在");
        }
        FlowElement sourceFlowElement = sequenceFlow.getSourceFlowElement();
        executionEntity.setVariables(condition);
        // 将历史活动表更新
        historyManager.recordActivityEnd(executionEntity, "jump");
        // 设置当前流程
        executionEntity.setCurrentFlowElement(sourceFlowElement);
        // 触发执行实例运转, 第二个参数为是否参与计算
        agenda.planTakeOutgoingSequenceFlowsOperation(executionEntity, true);
        // 从runtime 表中删除当前任务
        taskEntityManager.delete(taskId);
        // 将历史任务表更新, 历史任务标记为完成
        historyManager.recordTaskEnd(taskId, "jump");
        return null;
    }
}
