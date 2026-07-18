package software.frisby.web.serial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenericTypeTest {
    @SuppressWarnings({"rawtypes"})
    private static final class RawSubclass extends GenericType {
    }

    private static abstract class MultiLevelStringListBase extends GenericType<List<String>> {
        MultiLevelStringListBase() {
            super();
        }
    }

    private static final class MultiLevelStringList extends MultiLevelStringListBase {
        MultiLevelStringList() {
            super();
        }
    }

    @Nested
    class AnonymousSubclass {
        @Test
        void simpleType_rawTypeIsCorrect() {
            GenericType<String> token = new GenericType<>() {
            };

            assertEquals(String.class, token.rawType());
        }

        @Test
        void simpleType_getTypeIsCorrect() {
            GenericType<String> token = new GenericType<>() {
            };

            assertEquals(String.class, token.type());
        }

        @Test
        void parameterizedType_rawTypeIsListClass() {
            GenericType<List<String>> token = new GenericType<>() {
            };

            assertEquals(List.class, token.rawType());
        }

        @Test
        void parameterizedType_getTypePreservesTypeArgument() {
            GenericType<List<String>> token = new GenericType<>() {
            };

            assertInstanceOf(ParameterizedType.class, token.type());

            ParameterizedType pt = (ParameterizedType) token.type();

            assertEquals(List.class, pt.getRawType());
            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }

        @Test
        void nestedParameterizedType_rawTypeIsMapClass() {
            GenericType<Map<String, List<Integer>>> token = new GenericType<>() {
            };

            assertEquals(Map.class, token.rawType());
        }

        @Test
        void arrayOfParameterizedType_rawTypeIsListArray() {
            GenericType<List<String>[]> token = new GenericType<>() {
            };

            assertEquals(List[].class, token.rawType());
        }

        @Test
        void arrayOfParameterizedType_getTypeIsGenericArrayType() {
            GenericType<List<String>[]> token = new GenericType<>() {
            };

            assertInstanceOf(GenericArrayType.class, token.type());
        }

        @Test
        void typeProvided_rawTypeIsListClass() {
            GenericType<List<String>> token = new GenericType<>() {
            };

            assertEquals(List.class, token.rawType());

            GenericType<List<String>> token2 = new GenericType<>(token.type()) {
            };

            assertEquals(List.class, token2.rawType());
            assertEquals(token.type(), token2.type());
        }
    }

    @Nested
    class NamedSubclass {
        @Test
        void namedSubclass_rawTypeIsListClass() {
            GenericType<List<String>> token = new StringListType();

            assertEquals(List.class, token.rawType());
        }

        @Test
        void namedSubclass_getTypePreservesTypeArgument() {
            GenericType<List<String>> token = new StringListType();

            assertInstanceOf(ParameterizedType.class, token.type());

            ParameterizedType pt = (ParameterizedType) token.type();

            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }

        @Test
        void multiLevelInheritance_rawTypeIsListClass() {
            GenericType<List<String>> token = new MultiLevelStringList();

            assertEquals(List.class, token.rawType());
        }

        @Test
        void multiLevelInheritance_getTypePreservesTypeArgument() {
            GenericType<List<String>> token = new MultiLevelStringList();

            assertInstanceOf(ParameterizedType.class, token.type());

            ParameterizedType pt = (ParameterizedType) token.type();

            assertEquals(String.class, pt.getActualTypeArguments()[0]);
        }

        private static final class StringListType extends GenericType<List<String>> {
        }
    }

    @Nested
    class ErrorCases {
        @Test
        void constructorWithNullType_throws() {
            assertThrows(
                    NullValueException.class,
                    () -> new GenericType<>(null) {
                    }
            );
        }

        @Test
        void rawSubclassWithoutTypeArg_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    RawSubclass::new
            );
        }

        @Test
        void getTypeClassWithCustomTypeImplementation_throwsIllegalArgumentException() {
            Type customType = new Type() {
                @Override
                public String getTypeName() {
                    return "custom";
                }
            };

            assertThrows(
                    IllegalArgumentException.class,
                    () -> GenericType.getTypeClass(customType)
            );
        }

        @Test
        void getTypeClassWithParameterizedTypeHavingNonClassRawType_throwsIllegalArgumentException() {
            ParameterizedType customParameterizedType = new ParameterizedType() {
                @Override
                public Type[] getActualTypeArguments() {
                    return new Type[0];
                }

                @Override
                public Type getRawType() {
                    return null;
                }

                @Override
                public Type getOwnerType() {
                    return null;
                }
            };

            assertThrows(
                    IllegalArgumentException.class,
                    () -> GenericType.getTypeClass(customParameterizedType)
            );
        }
    }

    @Nested
    class GetTypeClassEdgeCases {
        @Test
        void genericArrayType_returnsArrayClass() throws NoSuchFieldException {
            Type genArray = Holder.class.getDeclaredField("field").getGenericType();

            assertInstanceOf(GenericArrayType.class, genArray);
            assertEquals(List[].class, GenericType.getTypeClass(genArray));
        }

        @Test
        void typeVariable_throwsIllegalArgumentException() {
            TypeVariable<?> typeVar = List.class.getTypeParameters()[0];

            assertThrows(
                    IllegalArgumentException.class,
                    () -> GenericType.getTypeClass(typeVar)
            );
        }

        @SuppressWarnings("unused")
        private static class Holder {
            List<String>[] field;
        }
    }
}

