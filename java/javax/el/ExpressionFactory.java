/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.el;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

/**
 * 
 * @since 2.1
 */
public abstract class ExpressionFactory {

    private static final String SERVICE_RESOURCE_NAME =
        "META-INF/services/javax.el.ExpressionFactory";

    private static final String SEP = System.getProperty("file.separator");
    private static final String PROPERTY_FILE =
        System.getProperty("java.home") + "jre" + SEP + "lib" + SEP +
        "el.properties";
    private static final String PROPERTY_NAME = "javax.el.ExpressionFactory";

    public abstract Object coerceToType(Object obj, Class<?> expectedType)
            throws ELException;

    public abstract ValueExpression createValueExpression(ELContext context,
            String expression, Class<?> expectedType)
            throws NullPointerException, ELException;

    public abstract ValueExpression createValueExpression(Object instance,
            Class<?> expectedType);

    public abstract MethodExpression createMethodExpression(ELContext context,
            String expression, Class<?> expectedReturnType,
            Class<?>[] expectedParamTypes) throws ELException,
            NullPointerException;

    /**
     * Create a new {@link ExpressionFactory}. The class to use is determined by
     * the following search order:
     * <ol>
     * <li>services API (META-INF/services/javax.el.ExpressionFactory)</li>
     * <li>$JRE_HOME/lib/el.properties - key javax.el.ExpressionFactory</li>
     * <li>javax.el.ExpressionFactory</li>
     * <li>Platform default implementation -
     *     org.apache.el.ExpressionFactoryImpl</li>
     * </ol>
     * @return
     */
    public static ExpressionFactory newInstance() {
        return newInstance(null);
    }

    /**
     * Create a new {@link ExpressionFactory} passing in the provided
     * {@link Properties}. Search order is the same as {@link #newInstance()}.
     * 
     * @param properties
     * @return
     */
    public static ExpressionFactory newInstance(Properties properties) {
        String className = null;
        ExpressionFactory result = null;
        
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();

        // First services API
        className = getClassNameServices(tccl);
        if (className == null) {
            // Second el.properties file
            className = getClassNameJreDir();
        }
        if (className == null) {
            // Third system property 
            className = getClassNameSysProp();
        }
        if (className == null) {
            // Fourth - default
            className = "org.apache.el.ExpressionFactoryImpl";
        }
        
        try {
            Class<?> clazz = null;
            if (tccl == null) {
                clazz = Class.forName(className);
            } else {
                clazz = tccl.loadClass(className);
            }
            Constructor<?> constructor = null;
            // Do we need to look for a constructor that will take properties?
            if (properties != null) {
                try {
                    constructor = clazz.getConstructor(Properties.class);
                } catch (SecurityException se) {
                    throw new ELException(se);
                } catch (NoSuchMethodException nsme) {
                    // This can be ignored
                    // This is OK for this constructor not to exist
                }
            }
            if (constructor == null) {
                result = (ExpressionFactory) clazz.newInstance();
            } else {
                result =
                    (ExpressionFactory) constructor.newInstance(properties);
            }
            
        } catch (ClassNotFoundException e) {
            throw new ELException(
                    "Unable to find ExpressionFactory of type: " + className,
                    e);
        } catch (InstantiationException e) {
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + className,
                    e);
        } catch (IllegalAccessException e) {
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + className,
                    e);
        } catch (IllegalArgumentException e) {
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + className,
                    e);
        } catch (InvocationTargetException e) {
            throw new ELException(
                    "Unable to create ExpressionFactory of type: " + className,
                    e);
        }
        
        return result;
    }
    
    private static String getClassNameServices(ClassLoader tccl) {
        InputStream is = null;
        
        if (tccl == null) {
            is = ClassLoader.getSystemResourceAsStream(SERVICE_RESOURCE_NAME);
        } else {
            is = tccl.getResourceAsStream(SERVICE_RESOURCE_NAME);
        }

        if (is != null) {
            String line = null;
            try {
                BufferedReader br =
                    new BufferedReader(new InputStreamReader(is, "UTF-8"));
                line = br.readLine();
                if (line != null && line.trim().length() > 0) {
                    return line.trim();
                }
            } catch (UnsupportedEncodingException e) {
                // Should never happen with UTF-8
                // If it does - ignore & return null
            } catch (IOException e) {
                throw new ELException("Failed to read " + SERVICE_RESOURCE_NAME,
                        e);
            } finally {
                try {
                    is.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
        
        return null;
    }
    
    private static String getClassNameJreDir() {
        File file = new File(PROPERTY_FILE);
        if (file.canRead()) {
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                Properties props = new Properties();
                props.load(is);
                String value = props.getProperty(PROPERTY_NAME);
                if (value != null && value.trim().length() > 0) {
                    return value.trim();
                }
            } catch (FileNotFoundException e) {
                // Should not happen - ignore it if it does
            } catch (IOException e) {
                throw new ELException("Failed to read " + PROPERTY_FILE, e);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
        return null;
    }
    
    private static final String getClassNameSysProp() {
        String value = System.getProperty(PROPERTY_NAME);
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        return null;
    }
}
