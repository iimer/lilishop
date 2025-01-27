package cn.lili.modules.store.serviceimpl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.properties.RocketmqCustomProperties;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.utils.BeanUtil;
import cn.lili.modules.goods.entity.dos.Category;
import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.goods.service.CategoryService;
import cn.lili.modules.goods.service.GoodsService;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.search.utils.EsIndexUtil;
import cn.lili.modules.store.entity.dos.Store;
import cn.lili.modules.store.entity.dos.StoreDetail;
import cn.lili.modules.store.entity.dto.StoreAfterSaleAddressDTO;
import cn.lili.modules.store.entity.dto.StoreSettingDTO;
import cn.lili.modules.store.entity.vos.StoreBasicInfoVO;
import cn.lili.modules.store.entity.vos.StoreDetailVO;
import cn.lili.modules.store.entity.vos.StoreManagementCategoryVO;
import cn.lili.modules.store.entity.vos.StoreOtherVO;
import cn.lili.modules.store.mapper.StoreDetailMapper;
import cn.lili.modules.store.service.StoreDetailService;
import cn.lili.modules.store.service.StoreService;
import cn.lili.rocketmq.RocketmqSendCallbackBuilder;
import cn.lili.rocketmq.tags.GoodsTagsEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 店铺详细业务层实现
 *
 * @author pikachu
 * @since 2020-03-07 16:18:56
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class StoreDetailServiceImpl extends ServiceImpl<StoreDetailMapper, StoreDetail> implements StoreDetailService {

    /**
     * 店铺
     */
    @Autowired
    private StoreService storeService;
    /**
     * 分类
     */
    @Autowired
    private CategoryService categoryService;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public StoreDetailVO getStoreDetailVO(String storeId) {
        StoreDetailVO storeDetail = this.baseMapper.getStoreDetail(storeId);
        return storeDetail;
    }

    @Override
    public StoreDetailVO getStoreDetailVOByMemberId(String memberId) {
        return this.baseMapper.getStoreDetailByMemberId(memberId);
    }

    @Override
    public StoreDetail getStoreDetail(String storeId) {
        LambdaQueryWrapper<StoreDetail> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper.eq(StoreDetail::getStoreId, storeId);
        return this.getOne(lambdaQueryWrapper);
    }

    @Override
    public Boolean editStoreSetting(StoreSettingDTO storeSettingDTO) {
        AuthUser tokenUser = Objects.requireNonNull(UserContext.getCurrentUser());
        //修改店铺
        Store store = storeService.getById(tokenUser.getStoreId());
        BeanUtil.copyProperties(storeSettingDTO, store);
        boolean result = storeService.updateById(store);
        if (result) {
            this.updateStoreGoodsInfo(store);
        }
        return result;
    }

    public void updateStoreGoodsInfo(Store store) {

        goodsService.update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getStoreId, store.getId())
                .set(Goods::getStoreName, store.getStoreName())
                .set(Goods::getSelfOperated, store.getSelfOperated()));
        goodsSkuService.update(new LambdaUpdateWrapper<GoodsSku>()
                .eq(GoodsSku::getStoreId, store.getId())
                .set(GoodsSku::getStoreName, store.getStoreName())
                .set(GoodsSku::getSelfOperated, store.getSelfOperated()));

        Map<String, Object> updateIndexFieldsMap = EsIndexUtil.getUpdateIndexFieldsMap(
                MapUtil.builder().put("storeId", store.getId()).build(),
                MapUtil.builder().put("storeName", store.getStoreName()).put("selfOperated", store.getSelfOperated()).build());
        String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.UPDATE_GOODS_INDEX_FIELD.name();
        //发送mq消息
        rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(updateIndexFieldsMap), RocketmqSendCallbackBuilder.commonCallback());
    }

    @Override
    public StoreBasicInfoVO getStoreBasicInfoDTO(String storeId) {
        return this.baseMapper.getStoreBasicInfoDTO(storeId);
    }

    @Override
    public StoreAfterSaleAddressDTO getStoreAfterSaleAddressDTO() {
        String storeId = Objects.requireNonNull(UserContext.getCurrentUser()).getStoreId();
        return this.baseMapper.getStoreAfterSaleAddressDTO(storeId);
    }

    @Override
    public StoreAfterSaleAddressDTO getStoreAfterSaleAddressDTO(String id) {
        StoreAfterSaleAddressDTO storeAfterSaleAddressDTO = this.baseMapper.getStoreAfterSaleAddressDTO(id);
        if (storeAfterSaleAddressDTO == null) {
            storeAfterSaleAddressDTO = new StoreAfterSaleAddressDTO();
        }
        return storeAfterSaleAddressDTO;
    }

    @Override
    public boolean editStoreAfterSaleAddressDTO(StoreAfterSaleAddressDTO storeAfterSaleAddressDTO) {
        String storeId = Objects.requireNonNull(UserContext.getCurrentUser()).getStoreId();
        LambdaUpdateWrapper<StoreDetail> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(StoreDetail::getSalesConsigneeName, storeAfterSaleAddressDTO.getSalesConsigneeName());
        lambdaUpdateWrapper.set(StoreDetail::getSalesConsigneeAddressId, storeAfterSaleAddressDTO.getSalesConsigneeAddressId());
        lambdaUpdateWrapper.set(StoreDetail::getSalesConsigneeAddressPath, storeAfterSaleAddressDTO.getSalesConsigneeAddressPath());
        lambdaUpdateWrapper.set(StoreDetail::getSalesConsigneeDetail, storeAfterSaleAddressDTO.getSalesConsigneeDetail());
        lambdaUpdateWrapper.set(StoreDetail::getSalesConsigneeMobile, storeAfterSaleAddressDTO.getSalesConsigneeMobile());
        lambdaUpdateWrapper.eq(StoreDetail::getStoreId, storeId);
        return this.update(lambdaUpdateWrapper);
    }

    @Override
    public boolean updateStockWarning(Integer stockWarning) {
        String storeId = Objects.requireNonNull(UserContext.getCurrentUser()).getStoreId();
        LambdaUpdateWrapper<StoreDetail> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(StoreDetail::getStockWarning, stockWarning);
        lambdaUpdateWrapper.eq(StoreDetail::getStoreId, storeId);
        return this.update(lambdaUpdateWrapper);
    }

    @Override
    public List<StoreManagementCategoryVO> goodsManagementCategory(String storeId) {

        //获取顶部分类列表
        List<Category> categoryList = categoryService.firstCategory();
        //获取店铺信息
        StoreDetail storeDetail = this.getOne(new LambdaQueryWrapper<StoreDetail>().eq(StoreDetail::getStoreId, storeId));
        //获取店铺分类
        String[] storeCategoryList = storeDetail.getGoodsManagementCategory().split(",");
        List<StoreManagementCategoryVO> list = new ArrayList<>();
        for (Category category : categoryList) {
            StoreManagementCategoryVO storeManagementCategoryVO = new StoreManagementCategoryVO(category);
            for (String storeCategory : storeCategoryList) {
                if (storeCategory.equals(category.getId())) {
                    storeManagementCategoryVO.setSelected(true);
                }
            }
            list.add(storeManagementCategoryVO);
        }
        return list;
    }

    @Override
    public StoreOtherVO getStoreOtherVO(String storeId) {
        return this.baseMapper.getLicencePhoto(storeId);
    }

}