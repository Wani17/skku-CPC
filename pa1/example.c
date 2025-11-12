int a, b = 100;
float f = 3.14, g;

int func(int x) {
    int n, s = 0;	// func_B0

    if (x < 0) {	// func_B1
	n = 0;
    } else {		// func_B2
	n = -1;
    }

    while (n < 10) {	// func_B3
	s = s - n * b;	// func_B4
	n = n / f + 1;
    }
    return s;		// func_B5
}
