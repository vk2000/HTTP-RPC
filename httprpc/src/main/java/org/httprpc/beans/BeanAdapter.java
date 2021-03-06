/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.httprpc.beans;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class that presents the properties of a Java Bean object as a map.
 */
public class BeanAdapter extends AbstractMap<String, Object> {
    // List adapter
    private static class ListAdapter extends AbstractList<Object> {
        private List<?> list;
        private HashMap<Class<?>, HashMap<String, Method>> accessorCache;

        public ListAdapter(List<?> list, HashMap<Class<?>, HashMap<String, Method>> accessorCache) {
            this.list = list;
            this.accessorCache = accessorCache;
        }

        @Override
        public Object get(int index) {
            return adapt(list.get(index), accessorCache);
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private Iterator<?> iterator = list.iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Object next() {
                    return adapt(iterator.next(), accessorCache);
                }
            };
        }
    }

    // Map adapter
    private static class MapAdapter extends AbstractMap<Object, Object> {
        private Map<?, ?> map;
        private HashMap<Class<?>, HashMap<String, Method>> accessorCache;

        public MapAdapter(Map<?, ?> map, HashMap<Class<?>, HashMap<String, Method>> accessorCache) {
            this.map = map;
            this.accessorCache = accessorCache;
        }

        @Override
        public Object get(Object key) {
            return adapt(map.get(key), accessorCache);
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return new AbstractSet<Entry<Object, Object>>() {
                @Override
                public int size() {
                    return map.size();
                }

                @Override
                public Iterator<Entry<Object, Object>> iterator() {
                    return new Iterator<Entry<Object, Object>>() {
                        private Iterator<? extends Entry<?, ?>> iterator = map.entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Entry<Object, Object> next() {
                            return new Entry<Object, Object>() {
                                private Entry<?, ?> entry = iterator.next();

                                @Override
                                public Object getKey() {
                                    return entry.getKey();
                                }

                                @Override
                                public Object getValue() {
                                    return adapt(entry.getValue(), accessorCache);
                                }

                                @Override
                                public Object setValue(Object value) {
                                    throw new UnsupportedOperationException();
                                }
                            };
                        }
                    };
                }
            };
        }
    }

    private Object bean;
    private HashMap<Class<?>, HashMap<String, Method>> accessorCache;

    private HashMap<String, Method> accessors;

    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";

    /**
     * Constructs a new Bean adapter.
     *
     * @param bean
     * The source Bean.
     */
    public BeanAdapter(Object bean) {
        this(bean, new HashMap<>());
    }

    private BeanAdapter(Object bean, HashMap<Class<?>, HashMap<String, Method>> accessorCache) {
        if (bean == null) {
            throw new IllegalArgumentException();
        }

        this.bean = bean;
        this.accessorCache = accessorCache;

        Class<?> type = bean.getClass();

        if (accessors == null) {
            accessors = new HashMap<>();

            Method[] methods = type.getMethods();

            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];

                if (method.getDeclaringClass() != Object.class) {
                    String key = getKey(method);

                    if (key != null) {
                        accessors.put(key, method);
                    }
                }
            }

            accessorCache.put(type, accessors);
        }
    }

    private static String getKey(Method method) {
        if (method.getParameterCount() > 0) {
            return null;
        }

        Key key = method.getAnnotation(Key.class);

        if (key == null) {
            String methodName = method.getName();

            String prefix;
            if (methodName.startsWith(GET_PREFIX)) {
                prefix = GET_PREFIX;
            } else if (methodName.startsWith(IS_PREFIX)) {
                prefix = IS_PREFIX;
            } else {
                return null;
            }

            int j = prefix.length();
            int n = methodName.length();

            if (j == n) {
                return null;
            }

            char c = methodName.charAt(j++);

            if (j == n || Character.isLowerCase(methodName.charAt(j))) {
                c = Character.toLowerCase(c);
            }

            return c + methodName.substring(j);
        } else {
            return key.value();
        }
    }

    /**
     * Retrieves a Bean property value. If the value is <tt>null</tt> or an
     * instance of one of the following types, it is returned as-is:
     * <ul>
     * <li>{@link String}</li>
     * <li>{@link Number}</li>
     * <li>{@link Boolean}</li>
     * <li>{@link Date}</li>
     * <li>{@link LocalDate}</li>
     * <li>{@link LocalTime}</li>
     * <li>{@link LocalDateTime}</li>
     * </ul>
     * If the value is a {@link List}, it is wrapped in an adapter that will
     * adapt the list's elements. If the value is a {@link Map}, it is wrapped
     * in an adapter that will adapt the map's values. Otherwise, the value is
     * considered a nested Bean and is wrapped in a {@link BeanAdapter}.
     *
     * @param key
     * The property name.
     *
     * @return
     * The property value.
     */
    @Override
    public Object get(Object key) {
        if (key == null) {
            throw new IllegalArgumentException();
        }

        Method method = accessors.get(key);

        Object value;
        if (method != null) {
            try {
                value = adapt(method.invoke(bean), accessorCache);
            } catch (InvocationTargetException | IllegalAccessException exception) {
                throw new RuntimeException(exception);
            }
        } else {
            value = null;
        }

        return value;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new AbstractSet<Entry<String, Object>>() {
            @Override
            public int size() {
                return accessors.size();
            }

            @Override
            public Iterator<Entry<String, Object>> iterator() {
                return new Iterator<Entry<String, Object>>() {
                    private Iterator<String> keys = accessors.keySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public Entry<String, Object> next() {
                        String key = keys.next();

                        return new SimpleImmutableEntry<>(key, get(key));
                    }
                };
            }
        };
    }

    /**
     * Adapts a list instance.
     *
     * @param list
     * The list to adapt.
     *
     * @return
     * A list implementation that will adapt the list's elements as documented for
     * {@linkplain BeanAdapter#get(Object)}.
     */
    public static List<?> adapt(List<?> list) {
        if (list == null) {
            throw new IllegalArgumentException();
        }

        return new ListAdapter(list, new HashMap<>());
    }

    /**
     * Adapts a map instance.
     *
     * @param map
     * The map to adapt.
     *
     * @return
     * A map implementation that will adapt the map's values as documented for
     * {@linkplain BeanAdapter#get(Object)}.
     */
    public static Map<?, ?> adapt(Map<?, ?> map) {
        if (map == null) {
            throw new IllegalArgumentException();
        }

        return new MapAdapter(map, new HashMap<>());
    }

    private static Object adapt(Object value, HashMap<Class<?>, HashMap<String, Method>> accessorCache) {
        if (value == null
            || value instanceof String
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Date
            || value instanceof LocalDate
            || value instanceof LocalTime
            || value instanceof LocalDateTime) {
            return value;
        } else if (value instanceof List<?>) {
            return new ListAdapter((List<?>)value, accessorCache);
        } else if (value instanceof Map<?, ?>) {
            return new MapAdapter((Map<?, ?>)value, accessorCache);
        } else {
            return new BeanAdapter(value, accessorCache);
        }
    }

    /**
     * Adapts a value for typed access. If the value is already an instance of
     * the given type, it is returned as-is. Otherwise:
     * <ul>
     * <li>If the target type is a number, the value is coerced using the
     * appropriate conversion method.</li>
     * <li>If the target type is {@link Date}, the value is coerced to a long
     * value and passed to {@link Date#Date(long)}.</li>
     * <li>If the target type is {@link LocalDate}, the value is parsed using
     * {@link LocalDate#parse(CharSequence)}.</li>
     * <li>If the target type is {@link LocalTime}, the value is parsed using
     * {@link LocalTime#parse(CharSequence)}.</li>
     * <li>If the target type is {@link LocalDateTime}, the value is parsed using
     * {@link LocalDateTime#parse(CharSequence)}.</li>
     *</ul>
     * If the target type is a {@link List}, the value is wrapped in an adapter
     * that will adapt the list's elements. If the target type is a {@link Map},
     * the value is wrapped in an adapter that will adapt the map's values.
     * Otherwise, the value is considered a nested Bean and is adapted by a
     * dynamic proxy instance that implements the given interface.
     *
     * @param <T>
     * The target type.
     *
     * @param value
     * The value to adapt.
     *
     * @param type
     * The target type.
     *
     * @return
     * An instance of the given type that adapts the given value.
     */
    @SuppressWarnings("unchecked")
    public static <T> T adapt(Object value, Type type) {
        if (type instanceof Class<?>) {
            return (T)adapt(value, (Class<?>)type);
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType)type;

            return (T)adapt(value, wildcardType.getUpperBounds()[0]);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType)type;

            Type rawType = parameterizedType.getRawType();

            if (rawType == List.class) {
                return (T)adaptList((List<?>)value, parameterizedType.getActualTypeArguments()[0]);
            } else if (rawType == Map.class) {
                return (T)adaptMap((Map<?, ?>)value, parameterizedType.getActualTypeArguments()[1]);
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static Object adapt(Object value, Class<?> type) {
        if (type.isInstance(value)) {
            return value;
        } else if (type == Byte.TYPE || type == Byte.class) {
            return (value == null) ? 0 : ((Number)value).byteValue();
        } else if (type == Short.TYPE || type == Short.class) {
            return (value == null) ? 0 : ((Number)value).shortValue();
        } else if (type == Integer.TYPE || type == Integer.class) {
            return (value == null) ? 0 : ((Number)value).intValue();
        } else if (type == Long.TYPE || type == Long.class) {
            return (value == null) ? 0 : ((Number)value).longValue();
        } else if (type == Float.TYPE || type == Float.class) {
            return (value == null) ? 0 : ((Number)value).floatValue();
        } else if (type == Double.TYPE || type == Double.class) {
            return (value == null) ? 0 : ((Number)value).doubleValue();
        } else if (type == Boolean.TYPE) {
            return (value == null) ? false : ((Boolean)value).booleanValue();
        } else if (type == Date.class) {
            return (value == null) ? null : new Date(((Number)value).longValue());
        } else if (type == LocalDate.class) {
            return (value == null) ? null : LocalDate.parse(value.toString());
        } else if (type == LocalTime.class) {
            return (value == null) ? null : LocalTime.parse(value.toString());
        } else if (type == LocalDateTime.class) {
            return (value == null) ? null : LocalDateTime.parse(value.toString());
        } else if (type.isInterface()){
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
                    String key = getKey(method);

                    if (key == null) {
                        throw new UnsupportedOperationException();
                    }

                    return adapt(((Map<?, ?>)value).get(key), method.getGenericReturnType());
                }
            }));
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Adapts a list instance for typed access.
     *
     * @param <E>
     * The target element type.
     *
     * @param list
     * The list to adapt.
     *
     * @param elementType
     * The target element type.
     *
     * @return
     * An list implementation that will adapt the list's elements as documented for
     * {@link #adapt(Object, Type)}.
     */
    public static <E> List<E> adaptList(List<?> list, Type elementType) {
        if (list == null) {
            throw new IllegalArgumentException();
        }

        if (elementType == null) {
            throw new IllegalArgumentException();
        }

        return new AbstractList<E>() {
            @Override
            public E get(int index) {
                return adapt(list.get(index), elementType);
            }

            @Override
            public int size() {
                return list.size();
            }

            @Override
            public Iterator<E> iterator() {
                return new Iterator<E>() {
                    private Iterator<?> iterator = list.iterator();

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public E next() {
                        return adapt(iterator.next(), elementType);
                    }
                };
            }
        };
    }

    /**
     * Adapts a map instance for typed access.
     *
     * @param <K>
     * The key type.
     *
     * @param <V>
     * The target value type.
     *
     * @param map
     * The map to adapt.
     *
     * @param valueType
     * The target value type.
     *
     * @return
     * An map implementation that will adapt the map's values as documented for
     * {@link #adapt(Object, Type)}.
     */
    public static <K, V> Map<K, V> adaptMap(Map<K, ?> map, Type valueType) {
        if (map == null) {
            throw new IllegalArgumentException();
        }

        if (valueType == null) {
            throw new IllegalArgumentException();
        }

        return new AbstractMap<K, V>() {
            @Override
            public V get(Object key) {
                return adapt(map.get(key), valueType);
            }

            @Override
            public Set<Entry<K, V>> entrySet() {
                return new AbstractSet<Entry<K, V>>() {
                    @Override
                    public int size() {
                        return map.size();
                    }

                    @Override
                    public Iterator<Entry<K, V>> iterator() {
                        return new Iterator<Entry<K, V>>() {
                            private Iterator<? extends Entry<K, ?>> iterator = map.entrySet().iterator();

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public Entry<K, V> next() {
                                return new Entry<K, V>() {
                                    private Entry<K, ?> entry = iterator.next();

                                    @Override
                                    public K getKey() {
                                        return entry.getKey();
                                    }

                                    @Override
                                    public V getValue() {
                                        return adapt(entry.getValue(), valueType);
                                    }

                                    @Override
                                    public V setValue(V value) {
                                        throw new UnsupportedOperationException();
                                    }
                                };
                            }
                        };
                    }
                };
            }
        };
    }
}
