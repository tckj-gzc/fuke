package com.o2o.shopcart.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.o2o.account.entity.Member;
import com.o2o.account.service.MemberService;
import com.o2o.base.controller.BaseController;
import com.o2o.base.json.JMapper;
import com.o2o.product.entity.ProductSku;
import com.o2o.product.service.ProductSkuService;
import com.o2o.shopcart.entity.ShopCart;
import com.o2o.shopcart.json.JGroupShopCart;
import com.o2o.shopcart.json.JShopCart;
import com.o2o.shopcart.json.JStoreList;
import com.o2o.shopcart.service.ShopCartService;
import com.o2o.supermarket.service.ActivityProductToStoreService;
import com.o2o.utils.json.JsonData;
import com.o2o.utils.json.JsonError;
import com.o2o.utils.json.JsonNoLogin;
import com.o2o.utils.json.JsonObject;
import com.o2o.utils.json.JsonSuccess;
import com.tgcommon.marketing.StoreUtils;

/**
 * 
 * @ClassName: ShopCartController
 * @Description: TODO
 * @author 
 * @date: 2014-10-11 13:59:04
 */
@Controller
@RequestMapping("shopCart")
public class ShopCartController extends BaseController {

	@Autowired
	private ShopCartService shopCartService;

	@Autowired
	private ActivityProductToStoreService activityProductToStoreService;

	@Autowired
	private ProductSkuService productSkuService;

	@Autowired
	private MemberService mebmerService;

	@RequestMapping(value = "/add", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject addCart(@Validated JShopCart jShopCart, HttpServletRequest request) {
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}
//		orderService.verifyPurchasedCount(memberId, jShopCart.getActivityProductId(), jShopCart.getSource(), jShopCart.getQuantity());
		// 如果已经存在就直接加数量
		ShopCart shopCart = this.shopCartService.findExist(jShopCart.getFkStoreId(), jShopCart.getActivityProductId(), memberId,
				jShopCart.getSkuId());
		if (shopCart != null) {
			this.updateQty(shopCart, jShopCart.getQuantity() + shopCart.getQuantity());
			return new JsonSuccess();
		}

		// 不同店铺不能共享购物车
//		Boolean existStore = this.shopCartService.findExsitStore(jShopCart.getFkStoreId(), memberId);
//		if (existStore) {
//			return new JsonError("购物车存在其他门店商品,清理了才能逛其他店,不能贪心哦");
//		}

		if (jShopCart.getSource() == StoreUtils.STORE_TYPE.SHOPPING_MALL) {
			// MallActivitySku activitySku =
			// this.mallActivitySkuService.getSku(jShopCart.getActivityProductId(),
			// jShopCart.getSkuId());
			// if (activitySku == null) {
			// return new JsonError("未找到购买的SKU");
			// }
			// if (activitySku.getRealRemain() <= jShopCart.getQuantity()) {
			// return new JsonError("只剩下" + activitySku.getRealRemain() +
			// "库存,无法购买");
			// }
			// attributeStr = activitySku.getSkuAttrStr();
			return new JsonError("该商品无法添加购物车,喜欢就立即抢购吧");
		} else {
			this.activityProductToStoreService.validateQty(jShopCart.getFkStoreId(), jShopCart.getActivityProductId(),
					jShopCart.getQuantity());
		}

		ProductSku productSku = this.productSkuService.getById(jShopCart.getSkuId());
		if (productSku == null) {
			return new JsonError("找不到SKU:" + jShopCart.getSkuId());
		}
		this.shopCartService.addShopCart(jShopCart, productSku.getSkuAttributeStr(), productSku.getFkProductId(), memberId,true);
		return new JsonSuccess();
	}

	private void updateQty(ShopCart shopCart, Integer quantity) {
		shopCart.setQuantity(quantity);
		this.activityProductToStoreService.validateQty(shopCart.getFkStoreId(), shopCart.getActivityProductId(), shopCart.getQuantity());
		this.shopCartService.updShopCart(shopCart);
	}

	@RequestMapping(value = "/list", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject list(HttpServletRequest request, String idSetStr) {
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}

		Set<Long> idSet = new HashSet<Long>();
		if (idSetStr != null) {
			String[] idArr = StringUtils.split(idSetStr, ",");
			for (String id : idArr) {
				if (StringUtils.isNumeric(id))
					idSet.add(new Long(id));
			}
		}

		List<ShopCart> shopCartList = this.shopCartService.listActive(memberId, idSet);
		if (shopCartList == null || shopCartList.size() == 0) {
			return new JsonData(null);
		}
		

		
		this.shopCartService.fillShopCart(shopCartList);
		Map<String, List<ShopCart>> groupShopCart = new HashMap<String, List<ShopCart>>();
		for (ShopCart shopCart : shopCartList) {
			String key = shopCart.getFkStoreId() + "," + shopCart.getNeedPay();
			if (groupShopCart.get(key) == null) {
				groupShopCart.put(key, new ArrayList<ShopCart>());
			}
			groupShopCart.get(key).add(shopCart);
		}

		List<JGroupShopCart> result = new ArrayList<JGroupShopCart>();
		for (String key : groupShopCart.keySet()) {
			ShopCart shopCart = groupShopCart.get(key).get(0);
			JGroupShopCart jGroupShopCart = new JGroupShopCart();
			jGroupShopCart.setNeedPay(shopCart.getNeedPay());
			jGroupShopCart.setStoreId(shopCart.getFkStoreId());
			jGroupShopCart.setStoreName(shopCart.getStoreName());
			jGroupShopCart.setShopCartList(groupShopCart.get(key));
			result.add(jGroupShopCart);
		}
		return new JsonData(result);
	}

	@RequestMapping(value = "/listExpired", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject listExpired(HttpServletRequest request) {
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}
		List<ShopCart> shopCartList = this.shopCartService.queryExpired(memberId);
		if (shopCartList == null || shopCartList.size() == 0) {
			return new JsonData(null);
		}
		this.shopCartService.fillShopCart(shopCartList);
		return new JsonData(shopCartList);
	}

	@RequestMapping(value = "/updateQty", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject updateQty(HttpServletRequest request, Long id, Integer quantity) {
		if (quantity == null || quantity <= 0) {
			return new JsonError("修改后数量必须大于0");
		}
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}

		ShopCart shopCart = this.shopCartService.getById(id, memberId);
		if (shopCart == null) {
			return new JsonError("找不到需修改的购物车");
		}
		this.updateQty(shopCart, quantity);
		return new JsonData(shopCart);
	}

	@RequestMapping(value = "/delete", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject delete(HttpServletRequest request, Long id) {
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}
		ShopCart shopCart = this.shopCartService.getById(id, memberId);
		if (shopCart == null) {
			return new JsonError("找不到需修改的购物车");
		}
		this.shopCartService.delete(id);
		return new JsonSuccess();
	}

	@RequestMapping(value = "/commit", method = RequestMethod.POST)
	@ResponseBody
	public JsonObject commit(HttpServletRequest request, String jsonStr) {
		Long memberId = this.getLoggedMemberId(request);
		if (memberId == null) {
			return new JsonNoLogin();
		}
		Member m = this.mebmerService.getById(memberId);
		if (m == null) {
			return new JsonNoLogin();
		}
		List<JGroupShopCart> jGroupShopCartList = JMapper.buildNonNullMapper().fromJsonToList(jsonStr, JGroupShopCart.class);
		return this.shopCartService.saveCommit(jGroupShopCartList, m);
	}

}