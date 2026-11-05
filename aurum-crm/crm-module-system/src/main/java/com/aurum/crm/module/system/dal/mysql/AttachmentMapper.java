package com.aurum.crm.module.system.dal.mysql;

import com.aurum.crm.module.system.dal.dataobject.AttachmentDO;
import com.aurum.crm.mybatis.core.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件 Mapper，对应表 {@code sys_attachment}。
 *
 * <p>包路径已在 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.aurum.crm.**.dal.mysql")} 覆盖范围内。
 *
 * <p>⚠️ 不要把实体挪进 {@code model} 包再照着 crm 模块的样子改 ——
 * system 模块是骨架期建立的，约定就是 {@code dal/dataobject} + {@code dal/mysql}。
 */
@Mapper
public interface AttachmentMapper extends BaseMapperX<AttachmentDO> {
}
