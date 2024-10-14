package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.entity.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice//全局异常处理
public class WebExceptionAdvice {

//    @ExceptionHandler(RuntimeException.class)
//    public Result handleRuntimeException(RuntimeException e) {
//        log.error(e.toString(), e);
//        return Result.fail("服务器异常");
//    }
    ErrorResponse illegalArgumentResponse = new ErrorResponse(new IllegalArgumentException("参数错误!"));
    @ExceptionHandler(value = Exception.class)// 拦截所有异常, 这里只是为了演示，一般情况下一个方法特定处理一种异常
    public ResponseEntity<ErrorResponse> exceptionHandler(Exception e) {

        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.status(400).body(illegalArgumentResponse);
        }
//        } else if (e instanceof ResourceNotFoundException) {
//            return ResponseEntity.status(404).body(resourseNotFoundResponse);
//        }
        return null;
    }
}
