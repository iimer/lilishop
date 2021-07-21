package cn.lili.controller.promotion;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.order.cart.entity.vo.FullDiscountVO;
import cn.lili.modules.promotion.entity.enums.PromotionStatusEnum;
import cn.lili.modules.promotion.entity.vos.FullDiscountSearchParams;
import cn.lili.modules.promotion.service.FullDiscountService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端,满额活动接口
 *
 * @author paulG
 * @date 2021/1/12
 **/
@RestController
@Api(tags = "管理端,满额活动接口")
@RequestMapping("/manager/promotion/fullDiscount")
public class FullDiscountManagerController {
    @Autowired
    private FullDiscountService fullDiscountService;

    @ApiOperation(value = "获取满优惠列表")
    @GetMapping
    public ResultMessage<IPage<FullDiscountVO>> getCouponList(FullDiscountSearchParams searchParams, PageVO page) {
        page.setNotConvert(true);
        return ResultUtil.data(fullDiscountService.getFullDiscountByPageFromMongo(searchParams, page));
    }

    @ApiOperation(value = "获取满优惠详情")
    @GetMapping("/{id}")
    public ResultMessage<FullDiscountVO> getCouponDetail(@PathVariable String id) {
        return ResultUtil.data(fullDiscountService.getFullDiscount(id));
    }

    @ApiOperation(value = "获取满优惠商品列表")
    @GetMapping("/goods/{id}")
    public ResultMessage<FullDiscountVO> getCouponGoods(@PathVariable String id) {
        return ResultUtil.data(fullDiscountService.getFullDiscount(id));
    }

    @ApiOperation(value = "修改满额活动状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "满额活动ID", required = true, paramType = "path"),
            @ApiImplicitParam(name = "promotionStatus", value = "满额活动状态", required = true, paramType = "path")
    })
    @PutMapping("/status/{id}/{promotionStatus}")
    public ResultMessage<Object> updateCouponStatus(@PathVariable String id, @PathVariable String promotionStatus) {
        if (fullDiscountService.updateFullDiscountStatus(id, PromotionStatusEnum.valueOf(promotionStatus))) {
            return ResultUtil.success(ResultCode.SUCCESS);
        }
        return ResultUtil.error(ResultCode.ERROR);
    }
}
