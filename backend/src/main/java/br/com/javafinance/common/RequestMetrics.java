package br.com.javafinance.common;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Request-scoped aggregates only: never stores SQL, parameters, bodies or user identifiers. */
@Configuration
public class RequestMetrics {
    public static final class Sample {public long connectionNanos,jdbcNanos;public int jdbcCalls;}
    private static final ThreadLocal<Sample> CURRENT=new ThreadLocal<>();
    public static Sample start(){var sample=new Sample();CURRENT.set(sample);return sample;}
    public static void clear(){CURRENT.remove();}
    @Bean static BeanPostProcessor timedDataSource(){return new BeanPostProcessor(){
        @Override public Object postProcessAfterInitialization(Object bean,String name){
            if(!(bean instanceof DataSource source))return bean;
            return new TimedDataSource(source);
        }
    };}
    private static final class TimedDataSource extends DelegatingDataSource implements AutoCloseable {
        private final DataSource source;
        TimedDataSource(DataSource source){super(source);this.source=source;}
        @Override public Connection getConnection() throws SQLException{return acquire(()->source.getConnection());}
        @Override public Connection getConnection(String user,String password) throws SQLException{return acquire(()->source.getConnection(user,password));}
        @Override public void close() throws Exception{if(source instanceof AutoCloseable closeable)closeable.close();}
    }
    @FunctionalInterface interface Open {Connection get() throws SQLException;}
    static Connection acquire(Open open) throws SQLException{
        Sample sample=CURRENT.get();if(sample==null)return open.get();long start=System.nanoTime();
        Connection connection;
        try{connection=open.get();}finally{sample.connectionNanos+=System.nanoTime()-start;}
        return (Connection)Proxy.newProxyInstance(RequestMetrics.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            Object result=invoke(connection,method,args);
            if(result instanceof Statement statement){
                Class<?> type=statement instanceof CallableStatement?CallableStatement.class:statement instanceof PreparedStatement?PreparedStatement.class:Statement.class;
                return Proxy.newProxyInstance(RequestMetrics.class.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
                    if(!m.getName().startsWith("execute"))return invoke(statement,m,a);
                    long began=System.nanoTime();try{return invoke(statement,m,a);}finally{sample.jdbcCalls++;sample.jdbcNanos+=System.nanoTime()-began;}
                });
            }
            return result;
        });
    }
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable{
        try{return method.invoke(target,args);}catch(InvocationTargetException error){throw error.getCause();}
    }
}
