#include <stdio.h>
#include <stdlib.h>

int main(void) {
#if FOO
	puts("!!!Hello World!!!  "); /* prints !!!Hello World!!! */
#endif
	return EXIT_SUCCESS;
}
