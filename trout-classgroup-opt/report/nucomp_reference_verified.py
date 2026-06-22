from math import isqrt
import random
exec(open("nudupl_verify.py").read().split("rng=random.Random(3)")[0])

def parteucl(a,b,L):
    v=0; d=a; v2=1; v3=b; z=0
    while abs(v3)>L:
        av3=abs(v3); t3=d%av3; q=(d-t3)//v3; t2=v-q*v2
        v=v2; d=v3; v2=t2; v3=t3; z+=1
    if z%2==1: v2=-v2; v3=-v3
    return v,d,v2,v3,z

def nucomp(f1,f2,D,L):
    a1,b1,c1=f1; a2,b2,c2=f2
    if a1<a2: a1,b1,c1,a2,b2,c2=a2,b2,c2,a1,b1,c1
    s=(b1+b2)//2; n=b2-s
    d,u,v=xgcd(a2,a1)                       # u*a2+v*a1=d
    if d==1:
        A=-u*n; d1=d
    elif s%d==0:
        A=-u*n; d1=d; a1//=d1; a2//=d1; s//=d1
    else:
        d1,u1,v1=xgcd(s,d)                  # u1*s+v1*d=d1
        if d1>1: a1//=d1; a2//=d1; s//=d1; d//=d1
        l=(-u1*((u*(c1%d)+v*(c2%d))%d))%d
        A=-u*(n//d)+l*(a1//d)
    A%=a1; A1=a1-A
    if A1<A: A=-A1
    v_,dd,v2_,v3_,z=parteucl(a1,A,L)
    if z==0:
        a3=dd*a2; b3=2*(a2*v3_)+b2
        return reduce_form(a3,b3,(b3*b3-D)//(4*a3))
    b=(a2*dd+n*v_)//a1
    e=(s*dd+c2*v_)//a1
    Q2=(b*v3_)+n; f=Q2//dd
    Q4=(e*v2_)-s; g=Q4//v_
    if d1>1: v2_=d1*v2_; v_=d1*v_
    a3=dd*b+e*v_
    b3=dd*f+v3_*b+e*v2_+g*v_
    c3=v3_*f+g*v2_
    return reduce_form(a3,b3,c3)

# FULL verification across many discriminants, generic + degenerate forms
def pforms(D,count,aMax=60000):
    out=[];p=3
    while len(out)<count and p<=aMax:
        if isprime(p) and D%p!=0 and legendre(D,p)==1:
            b1=tonelli(D%p,p)
            if b1 is not None:
                b=b1 if b1%2==1 else b1+p; b%=2*p
                if b>p:b-=2*p
                if (b*b-D)%(4*p)==0: out.append(reduce_form(p,b,(b*b-D)//(4*p)))
        p+=2
    return out

rng=random.Random(2024)
total=0; fails=0; zc={'z0':0,'zpos':0,'dgt1':0}
for trial in range(8):
    bits=rng.choice([120,160,200,240])
    D=make_disc(bits,rng); L=isqrt(isqrt(abs(D)//4))
    gens=pforms(D,20); pool=list(gens)
    for _ in range(60):
        f=rng.choice(gens)
        for _ in range(rng.randint(1,30)): f=compose_classic(f,rng.choice(gens),D)
        pool.append(f)
    # include identity and small forms (edge cases)
    pool.append(reduce_form(*gens[0]))
    for _ in range(300):
        x=rng.choice(pool); y=rng.choice(pool)
        got=nucomp(x,y,D,L); exp=compose_classic(x,y,D)
        total+=1
        if got!=exp:
            fails+=1
            if fails<=3: print("FAIL D bits",bits,"x",x,"y",y,"got",got,"exp",exp)
print(f"TOTAL {total}  FAILS {fails}")
print("100% correct!" if fails==0 else "needs fixing")
