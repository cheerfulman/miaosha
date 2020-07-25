package org.example.validator;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

@Component
public class ValidationImpl implements InitializingBean {
    private Validator validator;

    // 实现校验方法并返回校验结果
    public ValidationResult validate(Object bean){
        ValidationResult result = new ValidationResult();
        Set<ConstraintViolation<Object>> constrainViolationSet = validator.validate(bean);

        if(constrainViolationSet.size() > 0){
            // 代表有错误
            result.setHasErrors(true);
            constrainViolationSet.forEach(constrainViolation -> {
                String errMsg = constrainViolation.getMessage();
                String propertyName = constrainViolation.getPropertyPath().toString();
                result.getErrorMsgMap().put(propertyName,errMsg);
            });
        }
        return result;
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        // 将hibernate validator 通过工厂初始化方式使其实例化
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
}
