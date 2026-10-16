# Mathematical Foundations of Signal Attenuation

## 1. Classical Energy Relations
The total kinetic energy in relativistic mechanics is given by the celebrated relation:

$$ E = mc^2 $$

For an electromagnetic field propagating through an absorbing medium with absorption coefficient $\alpha$, the intensity $I(x)$ at depth $x$ satisfies the differential equation:

$$ \frac{dI}{dx} = -\alpha I(x) $$

Solving this relation yields the standard exponential decay law:

$$ I(x) = I_0 e^{-\alpha x} $$

## 2. Discrete Fourier Transform
For a discrete sequence $x_n$ of length $N$, the transform $X_k$ is computed as:

$$ X_k = \sum_{n=0}^{N-1} x_n e^{-i 2\pi k n / N} $$

where $i = \sqrt{-1}$.
