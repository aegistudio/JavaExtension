Java Language Feature Extension
===============================
  This project aims at providing language feature extensions to java.<br/>
  These feature are considered to be useful however not included or not easy to reach in java.<br/>
  The project attempts to make all source codes in java.<br/>
  
Meta-programming For Common Implementation
---------------------------------------
# Motivation
This feature comes into being when I notice that we need to augment a class to some interface sometimes. <br/>
The mostly talked scenario is stub-generating. When we want to call method in another process or host, we would like 
to generate a stub that call the middleware, then the middleware puts the callback message onto network, reach
the listening middleware on host or process, and finally invokes the method in the skeleton. <br/>

    private MiddlewareClient client;
    public void someMethod() throws IOException {
      // Get the stub that could reach the middleware.
      BusinessService service = client.getStub(BusinessService.class);
        
      // Call the stub method to generate signals.
      // And in the stub body, the code may be
      // public void doService(int a, int b) {
      //    this.output.writeInt(PTL_CALL);
      //    this.output.writeInt(IDX_METHOD_doService);
      //    this.output.writeInt(a);
      //    this.output.writeInt(b);
      //    ....
      // }
      service.doService(0, 2);
    }

Another scenario is the script engine, we would like some procedure to be written in script language (maybe 
interpreter mode), and then call the procedure in java code. And it would be not elegant if we use 
engine.call(<procedureName>, <params>); We would rather prefer <procedureName>(<params>);

    private ScriptEngine scriptEngine;
    
    // We would wan't this interface.
    public interface X {
      public int x(int y);
    }
    
    public void someMethod() {
      // We generates a function in script.
      scriptEngine.eval("function x(a) { return a + 1; }");
      
      // Now get a interface that calls the script code.
      X x = scriptEngine.toInterface(X.class);
      
      // The output would be 3.
      System.out.println(x.x(2));
    }

In the mentioned cases, we would notices the fact we are in need of a meta-programming mechanism with which we 
could stretch our class to some random interfaces. However, how we process callback will stays the same (Either 
in code generating or method body), which means we have a common implementation. <br/>
So the meta-programming mechanism is designed to fit the following cases:
> 1. Class in need of fitting in random interfaces.
> 2. Callback mechanism stays the same.
> 3. Code generating seldom varies.

# RuntimeClass
The <a href="https://github.com/aegistudio/JavaExtension/blob/master/src/net/aegistudio/meta/RuntimeClass.java">
net.aegistudio.meta.RuntimeClass</a> provides common method for generating codes on-the-fly. The class itself 
is abstract, and its subclasses tell it how to handle code generating work. The RuntimeClass is the cornerstone 
for common-implementation meta-programming. A sample code will help you to understand:

	public static void main(String[] arguments) throws Exception {
		RuntimeClass runtimeClass = new RuntimeClass() {
			@Override
			protected String[] imports(Class<?> incoming) {	return null; }

			@Override
			protected Class<?> superclass(Class<?> incoming) {	return null;	}

			@Override
			protected Class<?>[] interfaces(Class<?> incoming) {	return null;	}

			@Override
			protected String classdef(Class<?> incoming) {	return null;	}

			@Override
			protected String constructor(Class<?> incoming, Constructor<?>[] constructor) {		return null;	}

			@Override
			protected String method(Class<?> incoming, Method method) {
				StringBuilder builder = new StringBuilder();
				builder.append("System.out.print(\"");
				builder.append(incoming.getCanonicalName());
				builder.append('.');
				builder.append(method.getName());
				builder.append('(');
				builder.append("\");");
				
				for(int i = 0; i < method.getParameterCount(); i ++) {
					if(i > 0) builder.append("System.out.print(\", \");");
					builder.append("System.out.print(");
					builder.append("par");
					builder.append(i);
					builder.append(");");
				}
				
				builder.append("System.out.println(\");\");");
				
				if(!method.getReturnType().equals(void.class)) {
					if(method.getReturnType().isPrimitive())
						builder.append("return 0;");
					else builder.append("return null;");
				}
				return new String(builder);
			}
		};
		runtimeClass.newInstance(Runnable.class).run();
		System.out.println(runtimeClass.newInstance(Callable.class).call());
		System.out.println(runtimeClass.newInstance(Comparable.class).compareTo(1));
		System.out.println(runtimeClass.newInstance(Comparable.class).compareTo(2));
	}

And the output should be:

    java.lang.Runnable.run();
    java.util.concurrent.Callable.call();
    null
    java.lang.Comparable.compareTo(1);
    0
    java.lang.Comparable.compareTo(2);
    0

In the sample code, we generate the runtime class with its class body printing class name,
method name and parameter list, returning 0 or null if necessary.

#StretchingClass
It's inconvenient if we generate the code body by writing bundles of .append and \", and 
<a href="https://github.com/aegistudio/JavaExtension/blob/master/src/net/aegistudio/meta/StretchingClass.java">
net.aegistudio.meta.StretchingClass</a> provides another solution. Stretching class can also extends to 
interfaces, but all callback to interfaces instance will be routed to the stretching class itself.

	@SuppressWarnings("unchecked")
	public static void main(String[] arguments) throws Exception {
		StretchingClass runtimeClass = new StretchingClass() {
			@Override
			public Object call(Class<?> interfaceClass, Method callingMethod, Object... paramList) {
				System.out.print(interfaceClass.getCanonicalName());
				System.out.print('.'); System.out.print(callingMethod.getName());
				System.out.print('(');
				for(int i = 0; i < paramList.length; i ++){
					if(i > 0) System.out.print(", ");
					System.out.print(paramList[i]);
				}
				System.out.println(')');
				if(callingMethod.getReturnType().isPrimitive()) return 0;
				else return null;
			}
		};
		runtimeClass.augment(Runnable.class).run();
		System.out.println(runtimeClass.augment(Callable.class).call());
		System.out.println(runtimeClass.augment(Comparable.class).compareTo(1));
		System.out.println(runtimeClass.augment(Comparable.class).compareTo(2));
	}
	
The code should make the same effect as in the RuntimeClass. In fact, Stretching class is based on Runtime Class.
However, the concentration point of RuntimeClass is same code generating, and the one in StretchingClass is common
handle method.
