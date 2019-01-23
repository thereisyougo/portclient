package com.sunyard.port.client.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sunyard.port.client.entity.Transport;
import com.sunyard.port.client.entity.Transport.TransportRequest;
import com.sunyard.port.client.entity.Transport.TransportResponse;

public class ClassEmitter implements ApplicationContextAware {
	static org.slf4j.Logger log = LoggerFactory.getLogger(ClassEmitter.class);
	static Map<String, Class<?>> primitives = new HashMap<String, Class<?>>();
	static ApplicationContext applicationContext;

	private String invokeUrl;

	public void setInvokeUrl(final String invokeUrl) {
		this.invokeUrl = invokeUrl;
	}

	CustomHttpComponentsHttpInvokerRequestExecutor httpExecutor;

	public CustomHttpComponentsHttpInvokerRequestExecutor getHttpExecutor() {
		return httpExecutor;
	}

	public void setHttpExecutor(final CustomHttpComponentsHttpInvokerRequestExecutor httpExecutor) {
		this.httpExecutor = httpExecutor;
	}

	static {
		primitives.put(Byte.TYPE.getName(), byte.class);
		primitives.put(Short.TYPE.getName(), short.class);
		primitives.put(Integer.TYPE.getName(), int.class);
		primitives.put(Long.TYPE.getName(), long.class);
		primitives.put(Float.TYPE.getName(), float.class);
		primitives.put(Double.TYPE.getName(), double.class);
		primitives.put(Boolean.TYPE.getName(), boolean.class);
		primitives.put(Character.TYPE.getName(), char.class);
	}

	public <T> T wrap(final Class<T> interfaces) {
		return wrap(Thread.currentThread().getContextClassLoader(), interfaces);
	}

	@SuppressWarnings("unchecked")
	public <T> T wrap(final ClassLoader loader, final Class<T> interfaces) {
		return (T) Proxy.newProxyInstance(loader, new Class[] { interfaces }, new InvocationHandler() {
			@Override
			public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
				Transport.TransportRequest.Builder request = TransportRequest.newBuilder();
				request.setMethodSignature(sign(method));
				if (args != null) {
					ObjectMapper jsonMapper = Jackson2ObjectMapperBuilder.json().build();
					for (int i = 0; i < args.length; i++) {
						request.addParams(jsonMapper.writeValueAsString(args[i]));
					}
				}
				return remoteInvoke(method, request.build().toByteArray());
			}
		});
	}

	static Object returnType(final Method method, final String result) throws Throwable {
		ObjectMapper jsonMapper = Jackson2ObjectMapperBuilder.json().build();
		Type returnType = method.getGenericReturnType();
		if (returnType == Void.TYPE || "".equals(result))
			return null;
		else if (result.startsWith("ERR")) {
			String errResult = result.substring(3);
			ByteArrayInputStream input = new ByteArrayInputStream(Base64.decodeBase64(errResult));
			ObjectInputStream in = new ObjectInputStream(input);
			try {
				Object errObject = in.readObject();
				if (errObject instanceof Throwable)
					throw (Throwable) errObject;
			} finally {
				in.close();
			}
		}
		JavaType javaType = jsonMapper.getTypeFactory().constructType(returnType);
		return jsonMapper.readValue(result, javaType);
	}

	Object remoteInvoke(final Method method, final byte[] data) throws Throwable {
		byte[] result = httpExecutor.doPost(invokeUrl, data, "UTF-8");
		TransportResponse response = TransportResponse.parseFrom(result);
		return returnType(method, response.getResult());
	}

	public static String sign(final Method m0) {
		StringBuilder buf = new StringBuilder(256);
		buf.append(m0.getDeclaringClass().getName()).append(".").append(m0.getName()).append("(");
		Class<?>[] types = m0.getParameterTypes();
		int len = types.length;
		if (len > 0) {
			buf.append(types[0].getName());
			for (int i = 1; i < len; i++) {
				buf.append(",").append(types[i].getName());
			}
		}
		buf.append(")");
		return buf.toString();
	}

	public Method unsign(final String sign) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
		return unsign(Thread.currentThread().getContextClassLoader(), sign);
	}

	public Method unsign(final ClassLoader loader, final String sign)
			throws NoSuchMethodException, SecurityException, ClassNotFoundException {
		Method method;
		StringBuilder buf = new StringBuilder(sign);
		String classMethod = buf.substring(0, buf.indexOf("(")),
				className = classMethod.substring(0, classMethod.lastIndexOf(".")),
				methodName = classMethod.substring(className.length() + 1);
		String params = buf.substring(classMethod.length() + 1, buf.length() - 1);
		Class<?> clazz = Class.forName(className, true, loader);
		if (params.length() != 0) {
			String[] split = params.split(",");
			Class<?>[] parameterTypes = new Class[split.length];
			for (int i = 0; i < parameterTypes.length; i++) {
				parameterTypes[i] = convert(loader, split[i]);
			}
			method = getMethod(clazz, methodName, parameterTypes);
		} else
			method = getMethod(clazz, methodName);
		method.setAccessible(true);
		return method;
	}

	public Object invokeInServer(final byte[] data) throws InvalidProtocolBufferException, NoSuchMethodException, SecurityException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return invokeInServer(Thread.currentThread().getContextClassLoader(), data);
	}

	public byte[] pack(final Object obj) throws IOException {
		ObjectMapper jsonMapper = Jackson2ObjectMapperBuilder.json().build();
		String resultJson = jsonMapper.writeValueAsString(obj);
		TransportResponse.Builder responseBuilder = TransportResponse.newBuilder();
		if (obj instanceof Throwable) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(output);
			try {
				out.writeObject(obj);
			} finally {
				out.close();
			}
			StringBuilder buf = new StringBuilder("ERR");
			buf.append(Base64.encodeBase64String(output.toByteArray()));
			responseBuilder.setResult(buf.toString());
		} else {
			responseBuilder.setResult(resultJson);
		}
		TransportResponse response = responseBuilder.build();
		return response.toByteArray();
	}

	public Object invokeInServer(final ClassLoader loader, final byte[] data) throws InvalidProtocolBufferException, NoSuchMethodException, SecurityException, ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		TransportRequest request = TransportRequest.parseFrom(data);
		String sign = request.getMethodSignature();
		if (log.isDebugEnabled()) log.debug("ClassEmitter call: " + sign);
		Method method = unsign(loader, sign);
		int paramLength = request.getParamsCount();
		Object[] actualParams = new Object[paramLength];
		if (paramLength != 0) {
			ObjectMapper jsonMapper = Jackson2ObjectMapperBuilder.json().build();
			Type[] parameterTypes = method.getGenericParameterTypes();
			for (int i = 0; i < paramLength; i++) {
				String param = request.getParams(i);
				try {
					Object readValue = jsonMapper.readValue(param, jsonMapper.getTypeFactory().constructType(parameterTypes[i]));
					actualParams[i] = readValue;
				} catch (IOException e) {
					log.warn(e.toString(), e);
					actualParams[i] = null;
				}
			}
		}
		return invokeInContainer(loader, method, actualParams);
	}

	Object invokeInContainer(final ClassLoader loader, final Method method, final Object... params) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		Object object = applicationContext.getBean(method.getDeclaringClass());
		if (object != null)
			try {
				return method.invoke(object, params);
			} catch (Exception e) {
				return e;
			}
		return null;
	}

	Class<?> convert(final ClassLoader loader, final String className) throws ClassNotFoundException {
		if (primitives.containsKey(className))
			return primitives.get(className);
		else
			return Class.forName(className, true, loader);
	}

	Method getMethod(final Class<?> interfaces, final String methodName, final Class<?>... parameterTypes)
			throws NoSuchMethodException, SecurityException {
		Method method;
		if (parameterTypes == null || parameterTypes.length == 0) {
			try {
				method = interfaces.getMethod(methodName);
			} catch (Exception e) {
				method = interfaces.getDeclaredMethod(methodName);
			}
		} else {
			try {
				method = interfaces.getMethod(methodName, parameterTypes);
			} catch (Exception e) {
				method = interfaces.getDeclaredMethod(methodName, parameterTypes);
			}
		}
		return method;
	}

	public void call(final int[] a) {

	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
		ClassEmitter.applicationContext = applicationContext;
	}
}
