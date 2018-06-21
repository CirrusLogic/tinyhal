LIB = libaudiohalcm.so
SRC = audio_config.c
OBJ = ${SRC:.c=.o}
CFLAGS = -fPIC -Wall -Wextra -Wunused

CROSS_COMPILE =
CC = $(CROSS_COMPILE)gcc

LDFLAGS += -shared
RM = rm -f

TINYALSA_DIR =../../tinyalsa

INCLUDEDIRS = -I${TINYALSA_DIR}/include \
	   -I../include

.PHONY: all build clean
all: build

build: ${LIB}

${LIB}: ${OBJ}
	${CC} ${LDFLAGS} $^ -o $@  -lexpat -ltinyalsa

%.o: %.c
	${CC} ${CFLAGS} ${INCLUDEDIRS} -c -o $@ $<

clean:
	-${RM} ${LIB} ${OBJ}
