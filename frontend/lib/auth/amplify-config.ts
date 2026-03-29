import { Amplify } from 'aws-amplify';

export function configureAmplify() {
    const userPoolId = process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;
    const userPoolClientId = process.env.NEXT_PUBLIC_COGNITO_CLIENT_ID;
    const domain = process.env.NEXT_PUBLIC_COGNITO_DOMAIN;
    const region = process.env.NEXT_PUBLIC_AWS_REGION || 'ap-south-1';

    if (!userPoolId || !userPoolClientId) {
        console.warn('Cognito not configured — auth will be disabled');
        return;
    }

    Amplify.configure({
        Auth: {
            Cognito: {
                userPoolId,
                userPoolClientId,
                ...(domain ? {
                    loginWith: {
                        oauth: {
                            domain,
                            scopes: ['openid', 'email', 'profile'],
                            redirectSignIn: [typeof window !== 'undefined'
                                ? `${window.location.origin}/auth/callback`
                                : 'http://localhost:3000/auth/callback'],
                            redirectSignOut: [`${typeof window !== 'undefined' ? window.location.origin : 'http://localhost:3000'}/login`],
                            responseType: 'code',
                        },
                    },
                } : {}),
            },
        },
    });
}
