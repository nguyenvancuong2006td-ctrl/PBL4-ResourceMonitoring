package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.util.ArrayList;
import java.util.List;
import pbl4.agent.model.NetworkData;

public class NetworkMonitorService {

    private final SystemInfo systemInfo;

    public NetworkMonitorService() {
        systemInfo = new SystemInfo();
    }

    public List<NetworkData> collectNetworkData() {

        List<NetworkData> networkDataList =
                new ArrayList<>();

        List<NetworkIF> networkInterfaces =
                systemInfo.getHardware()
                        .getNetworkIFs();

        for (NetworkIF networkIF : networkInterfaces) {

            if (networkIF.getIfOperStatus()
                    != NetworkIF.IfOperStatus.UP) {
                continue;
            }

            String name =
                    networkIF.getName();

            String displayName =
                    networkIF.getDisplayName();

            long bytesReceived =
                    networkIF.getBytesRecv();

            long bytesSent =
                    networkIF.getBytesSent();

            NetworkData data =
                    new NetworkData(
                            name,
                            displayName,
                            bytesReceived,
                            bytesSent
                    );

            networkDataList.add(data);
        }

        return networkDataList;
    }
}