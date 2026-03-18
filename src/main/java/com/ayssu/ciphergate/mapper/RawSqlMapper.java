package com.ayssu.ciphergate.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RawSqlMapper {
    
    int executeRawSql(@Param("sql") String sql);
    
    Integer executeRawQueryForInteger(@Param("sql") String sql);
    
    String executeRawQueryForString(@Param("sql") String sql);
}