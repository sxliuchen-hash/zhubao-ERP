package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.common.api.ApiResponse;
import com.zhubao.erp.goldwork.app.GoldworkMaterialService;
import com.zhubao.erp.goldwork.domain.GoldworkMaterialLineView;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 金料来源。申报计量不入库存账。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/materials")
public class GoldworkMaterialController {

    private final GoldworkMaterialService materials;
    private final OperatorContextResolver actors;

    public GoldworkMaterialController(GoldworkMaterialService materials, OperatorContextResolver actors) {
        this.materials = materials;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<GoldworkMaterialLineView>> list(@PathVariable String goldworkCode, HttpServletRequest request) {
        return ApiResponse.ok(materials.list(actors.resolve(request), goldworkCode));
    }

    @PostMapping
    public ApiResponse<GoldworkMaterialLineView> receive(@PathVariable String goldworkCode, @RequestBody ReceiveRequest body,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(materials.receive(
                body.requestId(), actors.resolve(request), goldworkCode, body.sourceId(), body.sourceLineNo(),
                body.pieceCount(), body.netWeight(), body.grossWeight(), body.weightUnit(), body.purity()));
    }

    public record ReceiveRequest(
            String requestId,
            String sourceId,
            Integer sourceLineNo,
            BigDecimal pieceCount,
            BigDecimal netWeight,
            BigDecimal grossWeight,
            String weightUnit,
            String purity
    ) {
    }
}
