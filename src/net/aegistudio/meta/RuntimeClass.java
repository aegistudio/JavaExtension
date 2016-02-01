package net.aegistudio.meta;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Runtime class is the corner stone of my meta-programming
 * package, which is responsible for generating source code.
 * 
 * Just imagine we could generate any instance suits
 * an interface. However, method body generation of these
 * instances should be known to us.
 * 
 * @author aegistudio
 */

public abstract class RuntimeClass {
	/**
	 * Retrieves the import of the class to be instantialize.
	 * @param incoming the incoming class.
	 * @return the import list.
	 */
	protected abstract String[] imports(Class<?> incoming);
	
	/**
	 * Which class will this class to be inherited from. Please notice
	 * when incoming class is an abstract class, this method will not 
	 * be invoked.
	 * 
	 * @param incoming the incoming class.
	 * @return inherit the specified class when not null, and inherit 
	 * java.lang.Object when null is returned.
	 */
	protected abstract Class<?> superclass(Class<?> incoming);
	
	/**
	 * Which interfaces will this class implements. Please notice when the 
	 * incoming class is inside the returned interfaces, it will only appear
	 * once.
	 * 
	 * @param incoming the incoming class.
	 * @return implements interfaces when not null and not empty.
	 */
	protected abstract Class<?>[] interfaces(Class<?> incoming);
	
	/**
	 * Class definition includes field declaration, constructor block
	 * and and static block. Methods not suits the incoming'
	 * interfaces should also be included.
	 * 
	 * @param incoming the incoming class
	 * @return the class definition.
	 */
	protected abstract String classdef(Class<?> incoming);
	
	/**
	 * Constructor includes the constructor method body. Please don't
	 * include the constructor definition.
	 * 
	 * @param incoming the incoming class.
	 * @return the class definition.
	 */
	protected abstract String constructor(Class<?> incoming, Constructor<?>[] constructor);
	
	/**
	 * Method to be generated which is abstract for the incoming class. 
	 * Please don't include the method definition.
	 * The method definition will be ever <modifier> <return-type> 
	 * <method-name>(<param-type-0> par0, <param-type-1> par1, ......)
	 * throws <throws block>
	 * 
	 * @param incoming the incoming class.
	 * @param method method to be generated.
	 * @return the method body.
	 */
	protected abstract String method(Class<?> incoming, Method method);
	
	protected final HashMap<Class<?>, Class<?>> generatedClass = new HashMap<Class<?>, Class<?>>();
	
	/**
	 * Generate java.lang.Class object in order to generate an instance.
	 * Calling this method may be costly, please take care when calling.
	 * 
	 * @param incoming the incoming class.
	 * @return loaded class.
	 * @throws Exception if error generated while making this class.
	 */
	protected Class<?> loadClass(Class<?> incoming) throws Exception {
		Class<?> clazz = generatedClass.get(incoming);
		if(clazz == null) {
			// Precondition judging.
			if(incoming.isPrimitive()) throw new Exception("Could not generate instance for a primitive class!");
			if(incoming.isArray()) throw new Exception("Could not generate instance for an array.");
			
			// Generate temporary file.
			File java = File.createTempFile("rtc", ".java");
			String classname = java.getName();
			classname = classname.substring(0, classname.length() - ".java".length());
			
			// Generate java source.
			PrintStream source = new PrintStream(java);
			try {
				// Import section.
				String[] imports = this.imports(incoming);
				if(imports != null) for(String importEntry : imports)
					source.printf("import %s;\n", importEntry);
				
				// Class header section.
				boolean interfaceComing = incoming.isInterface();
				if(interfaceComing) {
					Class<?> superClazz = this.superclass(incoming);
					if(superClazz == null) source.printf("public class %s ", classname);
					
					else if(superClazz.isInterface()) throw new Exception("Could not extends an interface.");
					else source.printf("public class %s extends %s ", classname, superClazz.getCanonicalName());
				}
				else source.printf("public class %s extends %s ", classname, incoming.getCanonicalName());
				
				Class<?>[] interfaces = this.interfaces(incoming);
				TreeSet<String> interfaceList = new TreeSet<String>();
				if(interfaces != null) 
					for(Class<?> interfaceEntry : interfaces) {
						if(!interfaceEntry.isInterface())
							throw new Exception("Could not implements an non-interface.");
						interfaceList.add(interfaceEntry.getCanonicalName());
					}
				
				if(interfaceComing) 
					interfaceList.add(incoming.getCanonicalName());
				
				if(!interfaceList.isEmpty()) {
					source.print("implements ");
					boolean firstEntry = true;
					for(String interfaceEntry : interfaceList) {
						if(firstEntry) firstEntry = false;
						else source.print(", ");
						
						source.print(interfaceEntry);
					}
				}
				// Begin of class body.
				source.print("{");
				
					// Class def section.
					String classdef = this.classdef(incoming);
					if(classdef != null) source.println(classdef);
					
					// Constructor block.
					String constructor = this.constructor(incoming, incoming.getConstructors());
					if(constructor != null) {
						source.print("public ");
						source.print(classname);
						source.println("() {");
						source.println(constructor);
						source.print("}");
					}
					
					// Method block.
					Method[] methods = incoming.getDeclaredMethods();
					for(Method method : methods) 
						if(Modifier.isAbstract(method.getModifiers())) {
							if(Modifier.isPublic(method.getModifiers())) source.print("public ");
							if(Modifier.isProtected(method.getModifiers())) source.print("protected ");
							source.print(method.getReturnType().getSimpleName());
							source.print(" ");
							
							source.print(method.getName());
							source.print("(");
							for(int i = 0; i < method.getParameterCount(); i ++) {
								if(i > 0) source.print(", ");
								Parameter param = method.getParameters()[i];
								source.print(param.getType().getCanonicalName());
								source.print(" ");
								source.printf("par%d", i);
							}
							source.print(")");
							
							source.print("{");
							source.print(this.method(incoming, method));
							source.print("}");
					}
					
				// End of class body.
				source.print("}");
			}
			finally {
				source.close();
			}
			
			// Compile source code.
			int returnStatus = com.sun.tools.javac.Main.compile(new String[]{"-cp", 
					System.getProperty("java.class.path"), java.getPath()});
			if(returnStatus != 0) throw new Exception("Could not generate instance because of compilation error.");
			
			// Load generated class.
			URL parentUrl = java.getParentFile().toURI().toURL();
			URLClassLoader classloader = new URLClassLoader(new URL[]{parentUrl});
			generatedClass.put(incoming, clazz = classloader.loadClass(classname));
			classloader.close();
			
			// Make delete condition.
			new File(java.getParentFile(), classname.concat(".class")).deleteOnExit();
			java.delete();
		}
		return clazz;
	}
	
	/**
	 * Return an instance corresponding to the given class.
	 * 
	 * @param incoming the incoming object.
	 * @return a generated instance.
	 * @throws Exception error while generating object.
	 */
	public <T> T newInstance(Class<T> incoming) throws Exception {
		Object obj = this.loadClass(incoming).newInstance();
		return incoming.cast(obj);
	}
}
