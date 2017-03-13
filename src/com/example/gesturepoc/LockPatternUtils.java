package com.example.gesturepoc;

public class LockPatternUtils {
public static float getDistanceBetweenTwoPoints(float fpX,float fpY,float pX,float pY ) {
	return (float)Math.sqrt((pX-fpX)*(pX-fpX)+(pY-fpY)*(pY-fpY));
}
}
