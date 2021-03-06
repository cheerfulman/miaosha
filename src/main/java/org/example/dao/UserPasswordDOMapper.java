package org.example.dao;

import org.example.dataobject.UserPasswordDO;
import org.example.service.model.UserModel;

public interface UserPasswordDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    int insert(UserPasswordDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    int insertSelective(UserPasswordDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    UserPasswordDO selectByPrimaryKey(Integer id);

    UserPasswordDO selectByUserId(Integer id);


    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    int updateByPrimaryKeySelective(UserPasswordDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Mon Jul 06 15:06:09 CST 2020
     */
    int updateByPrimaryKey(UserPasswordDO record);
}