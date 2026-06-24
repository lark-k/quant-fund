package com.lk.quantfund.service;

import com.lk.quantfund.common.PageResponse;
import com.lk.quantfund.dto.system.DataSourceConfigRequest;
import com.lk.quantfund.vo.system.ApiCallLogVO;
import com.lk.quantfund.vo.system.DataSourceConfigVO;
import com.lk.quantfund.vo.system.OperationLogVO;
import java.util.List;

public interface SystemManagementService {

    List<DataSourceConfigVO> listDataSources();

    DataSourceConfigVO saveDataSourceConfig(DataSourceConfigRequest request);

    DataSourceConfigVO updateDataSourceConfig(Long configId, DataSourceConfigRequest request);

    PageResponse<OperationLogVO> operationLogs(Long pageNo, Long pageSize, String module, Boolean success);

    PageResponse<ApiCallLogVO> apiCallLogs(Long pageNo, Long pageSize, String provider, Boolean success);
}
