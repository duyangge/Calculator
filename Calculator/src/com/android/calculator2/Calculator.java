/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroupOverlay;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calculator2.CalculatorEditText.OnTextSizeChangeListener;
import com.android.calculator2.CalculatorExpressionEvaluator.EvaluateCallback;
/**
描述：
*/
public class Calculator extends Activity
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    private static final String NAME = Calculator.class.getName();//?
    /// M: limit the max input len
    private static final int MAX_INPUT_CHARACTERS = 100;//最大输入长度

    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";//当前值状态？？？
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";//当前值表达式

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;//无效值
	
	//设定计算器的四种状态：输入，计算，结果，错误
    private enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR
    }
    


    private CalculatorState mCurrentState;//当前计算器状态值：
	
    private CalculatorExpressionTokenizer mTokenizer;//适用于多语言环境替换字符
	
    private CalculatorExpressionEvaluator mEvaluator;//进行计算并返回结果

    private View mDisplayView;//输入与结果显示
	
    private CalculatorEditText mFormulaEditText;//定义表达式输入的文本框，主要用于改变文本字号
	
    private CalculatorEditText mResultEditText;//用于显示结果的文本框，主要用于改变文本字号
	
    private ViewPager mPadViewPager;//pad整体显示
	
    private View mDeleteButton;//删除按钮
	
    private View mClearButton;//清空按钮
	
    private View mEqualButton;//计算按钮

    private Animator mCurrentAnimator;//设置动画
	
	
	
   /**
	功能： 观察多个输入框（必填）是否输入内容，控制按钮是否可用
	*/
    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }
		//文本框发生变化后，设置计算器状态为可输入，
        @Override
        public void afterTextChanged(Editable editable) {
            setState(CalculatorState.INPUT);
            mEvaluator.evaluate(editable, Calculator.this);//边输入边计算结果
        }
    };
	
   //手机键盘进行监听的接口
    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {//按下松开后，文本框显示
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };
    
	//将得到的计算结果显示在文本框
    private final Editable.Factory mFormulaEditableFactory = new Editable.Factory() {
        @Override
        public Editable newEditable(CharSequence source) {
            final boolean isEdited = mCurrentState == CalculatorState.INPUT
                    || mCurrentState == CalculatorState.ERROR;
            return new CalculatorExpressionBuilder(source, mTokenizer, isEdited);
        }
    };


    //1创建
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        setContentView(R.layout.activity_calculator);// add layout
		
        mDisplayView = findViewById(R.id.display);
		
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
		
        mResultEditText = (CalculatorEditText) findViewById(R.id.result);
		
        mPadViewPager = (ViewPager) findViewById(R.id.pad_pager);
		
        mDeleteButton = findViewById(R.id.del);
		
        mClearButton = findViewById(R.id.clr);

        mEqualButton = findViewById(R.id.pad_numeric).findViewById(R.id.eq);
		
		//为空就得到pad_operator.xml中的mEqualButton的对象
        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(this);//选择语言
		
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);//表达式计算

        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
		
        setState(CalculatorState.values()[savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);
				
        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
				
        mEvaluator.evaluate(mFormulaEditText.getText(), this);

        mFormulaEditText.setEditableFactory(mFormulaEditableFactory);
		
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
		
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
		
        mFormulaEditText.setOnTextSizeChangeListener(this);
		
        /** M: add input length limitation @{ */
        mFormulaEditText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(MAX_INPUT_CHARACTERS) {
                    private static final int MAX_SUCCESSIVE_DIGITS = 10;

                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                            Spanned dest,
                            int dstart, int dend) {
                        if (mCurrentState == CalculatorState.INPUT) {
                            int keep = getMax() - (dest.length() - (dend - dstart));
                            if (keep >= end - start) {
                                if (TextUtils.isDigitsOnly(source)) {
                                    int digitKeep = MAX_SUCCESSIVE_DIGITS
                                            - (getCountLen(dest, dstart, dend) - (dend - dstart));
                                    if (digitKeep >= end - start) {
                                        return null;
                                    } else {
                                        mHandler.removeMessages(MAX_DIGITS_ALERT);
                                        mHandler.sendEmptyMessageDelayed(MAX_DIGITS_ALERT, TOAST_INTERVAL);
                                        vibrate();
                                        return "";
                                    }
                                } else {
                                    return null;
                                }
                            } else {
                                mHandler.removeMessages(MAX_INPUT_ALERT);
                                mHandler.sendEmptyMessageDelayed(MAX_INPUT_ALERT, TOAST_INTERVAL);
                                vibrate();
                                return "";
                            }
                        } else {
                            return null;
                        }
                    }

                    private int getCountLen(Spanned str, int start, int end) {
                        int len = str.length();
                        for (int i = len - 1; i > 0; i--) {
                            if (!Character.isDigit(str.charAt(i))) {
                                if (start <= i && i <= end) {
                                    continue;
                                } else {
                                    len = len - (i + 1);
                                    break;
                                }
                            }
                        }
                        return len;
                    }
                }
        });
        /** @} */
        mDeleteButton.setOnLongClickListener(this);
    }




    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, end it immediately to ensure the state is
        // up-to-date before it is serialized.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }

        super.onSaveInstanceState(outState);

        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getText().toString()));
    }
	
	
	
	/**
	
	*/
    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;

            if (state == CalculatorState.RESULT || state == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (state == CalculatorState.ERROR) {
                final int errorColor = getResources().getColor(R.color.calculator_error_color);
                mFormulaEditText.setTextColor(errorColor);
                mResultEditText.setTextColor(errorColor);
                getWindow().setStatusBarColor(errorColor);
            } else {
                mFormulaEditText.setTextColor(
                        getResources().getColor(R.color.display_formula_text_color));
                mResultEditText.setTextColor(
                        getResources().getColor(R.color.display_result_text_color));
                getWindow().setStatusBarColor(
                        getResources().getColor(R.color.calculator_accent_color));
            }
        }
    }

    /**
	
	*/
    @Override
    public void onBackPressed() {
        if (mPadViewPager == null || mPadViewPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first pad (or the pad is not paged),
            // allow the system to handle the Back button.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous pad.
            mPadViewPager.setCurrentItem(mPadViewPager.getCurrentItem() - 1);
        }
    }


    /**
	
	*/
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // If there's an animation in progress, end it immediately to ensure the state is
        // up-to-date before the pending user interaction is handled.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }


    /**
	
	*/
    public void onButtonClick(View view) {
        switch (view.getId()) {
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                break;
            case R.id.fun_cos:
            case R.id.fun_ln:
            case R.id.fun_log:
            case R.id.fun_sin:
            case R.id.fun_tan:
                // Add left parenthesis after functions.
                mFormulaEditText.append(((Button) view).getText() + "(");
                break;
            default:
                mFormulaEditText.append(((Button) view).getText());
                break;
        }
    }

    /**
	
	*/
    @Override
    public boolean onLongClick(View view) {
        if (view.getId() == R.id.del) {
            onClear();
            return true;
        }
        return false;
    }


    /**
	
	*/
    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (mCurrentState == CalculatorState.INPUT) {
            mResultEditText.setText(result);
        } else if (errorResourceId != INVALID_RES_ID) {
            onError(errorResourceId);
        } else if (!TextUtils.isEmpty(result)) {
            onResult(result);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }

        mFormulaEditText.requestFocus();
    }


    /**
	
	*/
    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) {
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }
	
	
	//判断当前状态是否为输入状态，是：进行计算，显示文本框结果
    private void onEquals() {
        if (mCurrentState == CalculatorState.INPUT) {
            setState(CalculatorState.EVALUATE);
            mEvaluator.evaluate(mFormulaEditText.getText(), this);
        }
    }
    
	/**
	
	*/
    private void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        final Editable formulaText = mFormulaEditText.getEditableText();
        final int formulaLength = formulaText.length();
        if (formulaLength > 0) {
            formulaText.delete(formulaLength - 1, formulaLength);
        }
    }


    /**
	
	*/
    private void reveal(View sourceView, int colorRes, AnimatorListener listener) {
        final ViewGroupOverlay groupOverlay =
                (ViewGroupOverlay) getWindow().getDecorView().getOverlay();

        final Rect displayRect = new Rect();
        mDisplayView.getGlobalVisibleRect(displayRect);

        // Make reveal cover the display and status bar.
        final View revealView = new View(this);
        revealView.setBottom(displayRect.bottom);
        revealView.setLeft(displayRect.left);
        revealView.setRight(displayRect.right);
        revealView.setBackgroundColor(getResources().getColor(colorRes));
        groupOverlay.add(revealView);

        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;

        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();

        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        final Animator revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_mediumAnimTime));
        alphaAnimator.addListener(listener);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(revealAnimator).before(alphaAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                groupOverlay.remove(revealView);
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }


    /**
	
	*/
    private void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getText())) {
            return;
        }

        final View sourceView = mClearButton.getVisibility() == View.VISIBLE
                ? mClearButton : mDeleteButton;
        reveal(sourceView, R.color.calculator_accent_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFormulaEditText.getEditableText().clear();
            }
        });
    }
    
	/**
	
	
	*/
    private void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            mResultEditText.setText(errorResourceId);
            return;
        }

        /** M: [ALPS01798852] Cannot start this animator on a detached or null view @{ */
        if (mEqualButton != null && mEqualButton.isAttachedToWindow()) {
            reveal(mEqualButton, R.color.calculator_error_color,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            setState(CalculatorState.ERROR);
                            mResultEditText.setText(errorResourceId);
                        }
                    });
        } else {
            mResultEditText.setText(errorResourceId);
            return;
        }
        /** @} */
    }
	
	/**
	
	*/
    private void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(result) / mResultEditText.getTextSize();
        final float resultTranslationX = (1.0f - resultScale) *
                (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingEnd());
        final float resultTranslationY = (1.0f - resultScale) *
                (mResultEditText.getHeight() / 2.0f - mResultEditText.getPaddingBottom()) +
                (mFormulaEditText.getBottom() - mResultEditText.getBottom()) +
                (mResultEditText.getPaddingBottom() - mFormulaEditText.getPaddingBottom());
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((int) valueAnimator.getAnimatedValue());
            }
        });

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                textColorAnimator,
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_Y, resultTranslationY),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y, formulaTranslationY));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mResultEditText.setText(result);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset all of the values modified during the animation.
                mResultEditText.setTextColor(resultTextColor);
                mResultEditText.setScaleX(1.0f);
                mResultEditText.setScaleY(1.0f);
                mResultEditText.setTranslationX(0.0f);
                mResultEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationY(0.0f);

                // Finally update the formula to use the current result.
                mFormulaEditText.setText(result);
                setState(CalculatorState.RESULT);

                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }


    //对超出后的长度的处理以及显示
    /** M: vibrate when input more than limited @{ */
    protected final static int MAX_DIGITS_ALERT = 0;
    protected final static int MAX_INPUT_ALERT = 1;
    protected final static int TOAST_INTERVAL = 500;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MAX_DIGITS_ALERT:
                    Toast.makeText(Calculator.this, R.string.max_digits_alert,
                            Toast.LENGTH_SHORT).show();
                    break;
                case MAX_INPUT_ALERT:
                    Toast.makeText(Calculator.this, R.string.max_input_alert,
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid message.what = "
                            + msg.what);
            }
        };
    };
    
	//振动器
    public void vibrate() {
        Vibrator vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(new long[] { 100, 100 }, -1);
        }
    }
    /** @} */
}
