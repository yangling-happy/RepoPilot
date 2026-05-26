package com.repopilot.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repopilot.business.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
