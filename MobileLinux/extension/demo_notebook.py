# %% [Cell 1: Basic Python & Math]
import math
print("🪐 Hello from Jupyter Notebook on MobileLinux!")
print(f"Pi value: {math.pi:.6f}")

# %% [Cell 2: Array Calculation & List Comprehension]
numbers = [x**2 for x in range(1, 11)]
print("Squares from 1 to 10:", numbers)
print("Sum of squares:", sum(numbers))

# %% [Cell 3: System & Hardware Information]
import platform
import sys
print("Operating System:", platform.system())
print("Machine Architecture:", platform.machine())
print("Python Version:", sys.version.split()[0])
