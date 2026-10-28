package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.domain.CustomerProfile;

/** 需要已知客户的测试显式建立档案。 */
public final class MasterDataFixture {
    private MasterDataFixture() { }
    public static InMemoryMasterDataRepository withCustomers() {
        var repository = new InMemoryMasterDataRepository();
        seed(repository);
        return repository;
    }
    public static void seed(com.zhubao.erp.business.domain.MasterDataRepository repository) {
        String[] names = {"恒信珠宝", "雅辰饰品", "璟瑞工贸", "瑞禾珠宝"};
        for (int i = 0; i < names.length; i++) repository.saveCustomer(new CustomerProfile(1001 + i, "C-" + (1001 + i), names[i], "", "", "", "", true));
    }
}
