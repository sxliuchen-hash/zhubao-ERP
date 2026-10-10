package com.zhubao.erp.business.web;

import com.zhubao.erp.business.app.ProgressBoardService;
import com.zhubao.erp.business.domain.ProgressRow;
import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在制进度一览。只读已有单据，不写金额。
 */
@RestController
@RequestMapping("/api/business/operations/progress")
public class ProgressBoardController {

    private final ProgressBoardService progressBoardService;
    private final OperatorContextResolver operatorContextResolver;

    public ProgressBoardController(
            ProgressBoardService progressBoardService,
            OperatorContextResolver operatorContextResolver
    ) {
        this.progressBoardService = progressBoardService;
        this.operatorContextResolver = operatorContextResolver;
    }

    @GetMapping
    public ApiResponse<List<ProgressRow>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(progressBoardService.list(
                operatorContextResolver.resolve(request),
                customer,
                process,
                dueDate,
                status
        ));
    }
}
