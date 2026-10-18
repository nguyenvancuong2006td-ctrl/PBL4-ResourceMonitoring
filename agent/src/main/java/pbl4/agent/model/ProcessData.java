package pbl4.agent.model;

public class ProcessData {

    private int processId;
    private String processName;
    private double cpuUsage;
    private long memoryUsage;

    public ProcessData() {
    }

    public ProcessData(
            int processId,
            String processName,
            double cpuUsage,
            long memoryUsage) {

        this.processId = processId;
        this.processName = processName;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public long getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(long memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    @Override
    public String toString() {

        return "ProcessData{" +
                "processId=" + processId +
                ", processName='" + processName + '\'' +
                ", cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                '}';
    }
}