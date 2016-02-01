package net.aegistudio.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Stretching class is able to augment its methods to provided 
 * interfaces. (Like providing java.lang.Runnable interface,
 * it will augment its method to that interface, and return a
 * Runnable instance.)
 * 
 * However, all augmented interfaces will call the 'call' method
 * of this class. Three parameters will be provided, the interface
 * class, the method, and the parameter list.
 * 
 * @author aegistudio
 */

public abstract class StretchingClass {
	
	private final RuntimeClass runtimeClass = new RuntimeClass() {
		@Override
		protected final String[] imports(Class<?> incoming) {	return null;	}
	
		@Override
		protected final Class<?> superclass(Class<?> incoming) {	return null;	}
	
		@Override
		protected final Class<?>[] interfaces(Class<?> incoming) {	return null;	}
	
		@Override
		protected final String classdef(Class<?> incoming) {
			StringBuilder classdef = new StringBuilder();
			
			// The stretching class.
			classdef.append("private ");
			classdef.append(StretchingClass.class.getCanonicalName());
			classdef.append(" stretchingClass;");
			
			// The interface class.
			classdef.append("private Class<?> interfaceClass = ");
			classdef.append(incoming.getCanonicalName());
			classdef.append(".class;");
			
			// The interface method.
			Method[] methods = incoming.getDeclaredMethods();
			for(Method method : methods) {
				classdef.append("private ");
				classdef.append(Method.class.getCanonicalName());
				classdef.append(" method_");
				classdef.append(method.getName());
				classdef.append(";");
			}
			
			return new String(classdef);
		}
	
		@Override
		protected String constructor(Class<?> incoming, Constructor<?>[] constructor) {
			return null;
		}
		
		@Override
		protected final String method(Class<?> incoming, Method method) {
			StringBuilder methodBuilder = new StringBuilder();
			if(!method.getReturnType().equals(void.class)) {
				methodBuilder.append("return (");
				methodBuilder.append(method.getReturnType().getCanonicalName());
				methodBuilder.append(")");
			}

			methodBuilder.append("stretchingClass.call(");
			methodBuilder.append("interfaceClass");
			methodBuilder.append(", method_");
			methodBuilder.append(method.getName());
			
			for(int i = 0; i < method.getParameterCount(); i ++) {
				methodBuilder.append(", ");
				methodBuilder.append("par");
				methodBuilder.append(i);
			}
			methodBuilder.append(");");
			return new String(methodBuilder);
		}
	};
	
	public abstract Object call(Class<?> interfaceClass, 
			Method callingMethod, Object... paramList);
	
	public <T> T augment(Class<T> interfaceClass) throws Exception {
		if(!interfaceClass.isInterface()) 
			throw new Exception("Could not augment to a non-interface");
		Object obj = this.runtimeClass.newInstance(interfaceClass);
		
		Field field = obj.getClass().getDeclaredField("stretchingClass");
		field.setAccessible(true);
		field.set(obj, this);
		field.setAccessible(false);
		
		Method[] methods = interfaceClass.getDeclaredMethods();
		for(Method method : methods) {
			Field methodField = obj.getClass()
					.getDeclaredField("method_".concat(method.getName()));
			methodField.setAccessible(true);
			methodField.set(obj, method);
			methodField.setAccessible(false);
		}
		
		return interfaceClass.cast(obj);
	}
}
