package org.example;

import org.example.dao.UserDOMapper;
import org.example.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello world!
 *
 */

@SpringBootApplication(scanBasePackages = "org.example")
@RestController
@MapperScan("org.example.dao")
public class App 
{
    @Autowired(required = false)
    private UserDOMapper userDOMapper;

    @RequestMapping("/")
    public String home(){
        UserDO user = userDOMapper.selectByPrimaryKey(1);
        if(user == null)return "用户不存在";
        else return user.getName();
    }
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        SpringApplication.run(App.class,args);
    }
}
