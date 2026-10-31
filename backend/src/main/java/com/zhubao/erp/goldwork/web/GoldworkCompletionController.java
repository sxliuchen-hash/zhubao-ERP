package com.zhubao.erp.goldwork.web;

import com.zhubao.erp.goldwork.app.GoldworkCompletionService;
import com.zhubao.erp.security.app.OperatorContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 金工结清。计酬未核定，这个入口不会把单据改成完成。 */
@RestController
@RequestMapping("/api/goldwork/orders/{goldworkCode}/completion")
public class GoldworkCompletionController {

    private final GoldworkCompletionService completion;
    private final OperatorContextResolver actors;

    public GoldworkCompletionController(GoldworkCompletionService completion, OperatorContextResolver actors) {
        this.completion = completion;
        this.actors = actors;
    }

    @PostMapping
    public void complete(@PathVariable String goldworkCode, @RequestBody CompleteRequest body, HttpServletRequest request) {
        completion.complete(actors.resolve(request), goldworkCode, body.countOutsourcingAsOutput());
    }

    public record CompleteRequest(String requestId, boolean countOutsourcingAsOutput) {
    }
}
