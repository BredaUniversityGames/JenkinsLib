#include "game.h"

#include <iostream>

int main()
{
    if (add(2, 2) != 4)
    {
        std::cerr << "add(2, 2) != 4\n";
        return 1;
    }
    std::cout << "All tests passed\n";
    return 0;
}
