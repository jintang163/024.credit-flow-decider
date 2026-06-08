package com.bc.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.credit.entity.LoanApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoanApplicationMapper extends BaseMapper<LoanApplication> {
}
