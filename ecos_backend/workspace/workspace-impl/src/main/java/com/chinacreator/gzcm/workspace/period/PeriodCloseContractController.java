package com.chinacreator.gzcm.workspace.period;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * 期间关账契约面 —— 详设《从零实施版》line 8268（第 12 篇 {@code closePeriod}）/
 * 附录A 第 34 条 {@code POST /api/v1/workspace/periods/{yyyMM}/close}（#146y）。
 *
 * <p><b>本类只有一条腿</b>：把契约路径接到 data-engine 属主面
 * （{@code POST /api/v1/datanet/facts/periods/{yyyMM}/close}），权限裁决、幂等判定、
 * 落库与审计事件全部由属主持有（{@link PeriodCloseGateway} 类注释记录了「为什么是薄承流」）。
 * workspace 侧不判角色、不查库、不写状态行。</p>
 *
 * <p><b>为什么类级前缀写 {@code /api/v1/workspace/periods} 而不是并入既有
 * {@code /api/v1/workspace} 面</b>：详设-07 D-4.2 与 {@code ScenarioActionController}
 * 已独占类级 {@code @RequestMapping("/api/v1/workspace")}，「同基路径禁双 Controller」
 * 是契约明文（line 4297 别名族纪律）；方法级路径若写成绝对路径则与前缀拼接成双前缀，
 * 故本面必须自带完整基路径，{@code /periods} 前缀当前无任何类占用。</p>
 *
 * <p><b>异常一律外抛，不在本面折叠</b>：契约错误档位 403/409 由
 * {@link com.chinacreator.gzcm.workspace.exception.PeriodCloseForwardException}
 * 携状态行交给 {@code WorkspaceExceptionHandler#handleDomain} 单源落状态，
 * 本类禁止出现 {@code ApiResponse.badRequest(...)}/{@code error(...)} 裸回
 * （X-16/X-17 与 {@code NoMachineCodeFactoryFaceRatchetTest} 判据）。</p>
 *
 * <p><b>不进 {@code SCENARIO_OPENAPI.md} 契约片段</b>：本条属第 12 篇（期间关账），
 * 而该片段按详设-07 D-4.1「只增不改」只承载 D-2 的 N1~N12，
 * {@code OpenApiPathParityTest} 的 12/6/11 三枚钉值因此不受本类影响（本类非契约属主类）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/periods")
public class PeriodCloseContractController {

    private final PeriodCloseGateway gateway;

    public PeriodCloseContractController(PeriodCloseGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 关账一个期间（operationId {@code closePeriod}）。
     *
     * @param yyyMM 路径变量，期间（{@code 202501}；归一与合法性由属主判定）
     * @param body  可空请求体（{@code reason}/{@code detailJson}），原样搬运属主字段面
     * @return 属主关账结果（幂等回放时 {@code alreadyClosed=true}）
     */
    @PostMapping("/{yyyMM}/close")
    public ApiResponse<Map<String, Object>> closePeriod(
            @PathVariable("yyyMM") String yyyMM,
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(gateway.close(yyyMM, body));
    }
}
