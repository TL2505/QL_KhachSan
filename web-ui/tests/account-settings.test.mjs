import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { api, setAuthSession, getAuthSession } from '../public/api.js';
import { renderAccountSettings } from '../public/js/shared/account-settings.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
const account = verified => ({userId:1, username:'synthetic', fullName:'Nguyễn Ánh',phone:'0900000000',email:'guest@example.invalid',emailVerified:verified,mailConfigured:true});
const challenge = purpose => ({challengeId:`synthetic-${purpose}-challenge`,maskedEmail:'g***@example.invalid',expiresAt:new Date(Date.now()+300000).toISOString(),resendAt:new Date(Date.now()+60000).toISOString()});
async function setup(t, role='ADMIN', verified=false, get, options={}) {
    const dom=new JSDOM('<main></main>',{url:'http://localhost'});
    const old={window:globalThis.window,document:globalThis.document,storage:globalThis.localStorage,get:api.get,post:api.post};
    globalThis.window=dom.window;globalThis.document=dom.window.document;globalThis.localStorage=dom.window.localStorage;
    setAuthSession({token:'synthetic-session',username:'synthetic',role});
    api.get=get||(async()=>account(verified));
    const calls=[];api.post=async(path,body,options)=>{calls.push({path,body,options});return path.endsWith('request')?challenge(path.includes('email')?'email':'password'):account(true);};
    t.after(()=>{api.get=old.get;api.post=old.post;dom.window.close();globalThis.window=old.window;globalThis.document=old.document;globalThis.localStorage=old.storage;});
    const root=document.querySelector('main');await renderAccountSettings(root,getAuthSession(),{showIdentity:role==='CUSTOMER',...options});
    const $=s=>root.querySelector(s);
    const submit=s=>$(s).dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    return {dom,root,$,submit,calls};
}

for(const role of ['ADMIN','STAFF','CUSTOMER'])test(`${role} shares email verification and password UX`,async t=>{
    const {$,submit,calls}=await setup(t,role);
    assert.equal($('.account-settings-view h1').textContent,'Hồ sơ & bảo mật');
    assert.equal($('#account-password-fields').disabled,true);
    assert.equal(!!$('.account-identity'),role==='CUSTOMER');
    $('#account-email-send').click();await settle();
    assert.equal($('#account-email-verify').hidden,false);assert.equal($('#account-email-resend').disabled,true);
    assert.equal($('#account-email-code').getAttribute('autocomplete'),'one-time-code');
    $('#account-email-code').value='123456';submit('#account-email-verify');await settle();
    assert.equal($('#account-password-fields').disabled,false);assert.equal($('#account-email-verify').hidden,true);
    assert.equal(getAuthSession().emailVerified,true);
    assert.equal(calls[1].body.challengeId,'synthetic-email-challenge');
    assert.equal(localStorage.getItem('hotel_auth_session').includes('123456'),false);
});

test('customer profile edits personal information without duplicating password or OTP controls',async t=>{
    const {$,submit,calls}=await setup(t,'CUSTOMER',false,null,{mode:'profile'});
    assert.equal($('.account-settings-view h1').textContent,'Hồ sơ cá nhân');
    assert.equal($('#account-password-request'),null);assert.equal($('#account-email-verify'),null);
    assert.equal($('#account-email-send'),null);assert.ok($('.account-identity'));
    $('#account-fullname').value='Tên mới';submit('#account-profile-form');await settle();
    assert.equal(calls[0].path,'/account/profile');assert.equal(calls[0].body.fullName,'Tên mới');
    assert.equal($('a[href="portal.html?view=security"]').textContent,'Đến Bảo mật & xác minh email');
});

test('customer security verifies a read-only email and unlocks password without a duplicate personal form',async t=>{
    const {$,submit,calls}=await setup(t,'CUSTOMER',false,null,{mode:'security',showIdentity:false});
    assert.equal($('.account-settings-view h1').textContent,'Bảo mật tài khoản');
    assert.equal($('#account-profile-form'),null);assert.equal($('#account-fullname'),null);assert.equal($('#account-phone'),null);
    assert.equal($('#account-email').readOnly,true);assert.equal($('#account-email').value,'guest@example.invalid');
    $('#account-email-send').click();await settle();$('#account-email-code').value='123456';submit('#account-email-verify');await settle();
    assert.equal($('#account-password-fields').disabled,false);assert.equal(calls[1].path,'/account/email/verify');
    assert.equal($('.account-identity'),null);
});

test('password sends current password first, changes only after OTP and clears the session',async t=>{
    const {$,root,submit,calls}=await setup(t,'STAFF',true);
    $('#current-password').value='Old-password';$('#new-password').value='New-password';$('#confirm-password').value='different';
    submit('#account-password-request');await settle();assert.equal(calls.length,0);
    $('#confirm-password').value='New-password';submit('#account-password-request');submit('#account-password-request');await settle();
    assert.equal(calls.length,1);assert.deepEqual(calls[0].body,{currentPassword:'Old-password'});
    assert.equal(calls[0].options.retryOnNetworkError,false);
    assert.equal($('#account-password-confirm').hidden,false);assert.equal($('#account-password-request').hidden,true);
    $('#account-password-code').value='654321';submit('#account-password-confirm');await settle();
    assert.deepEqual(calls[1].body,{challengeId:'synthetic-password-challenge',code:'654321',newPassword:'New-password'});
    assert.equal(getAuthSession(),null);assert.match(root.textContent,/Đã đổi mật khẩu/);
    assert.equal(root.querySelector('input'),null);
});

test('wrong OTP stays recoverable and never logs out the account',async t=>{
    const {$,submit}=await setup(t);$('#account-email-send').click();await settle();
    api.post=async()=>{throw Object.assign(new Error('Mã không đúng'),{status:422});};
    $('#account-email-code').value='111111';submit('#account-email-verify');await settle();
    assert.match($('#account-profile-message').textContent,/Mã không đúng/);
    assert.equal($('#account-email-verify').hidden,false);assert.equal($('#account-email-code').disabled,false);
    assert.equal(getAuthSession().token,'synthetic-session');
});

test('email edits require save before sending, invalidate verification and never render markup',async t=>{
    const {$,dom,submit}=await setup(t,'ADMIN',true);
    $('#account-email').value='other@example.invalid';$('#account-email').dispatchEvent(new dom.window.Event('input'));
    assert.equal($('#account-email-send').disabled,true);assert.match($('#account-email-help').textContent,/Lưu hồ sơ/);
    api.post=async(path,body)=>({...account(false),...body,fullName:'<img src=x onerror=alert(1)>'});
    submit('#account-profile-form');await settle();
    assert.equal($('#account-password-fields').disabled,true);assert.equal($('#account-display-name img'),null);
    assert.match($('#account-display-name').textContent,/<img/);assert.equal($('#account-email-send').disabled,false);
});

test('load failure offers retry and late results never touch a removed view',async t=>{
    const {$,root}=await setup(t,'ADMIN',false,async()=>{throw new Error('Offline');});
    assert.equal($('#account-reload').hidden,false);assert.equal($('#account-profile-fields').disabled,true);
    let finish;api.get=()=>new Promise(resolve=>finish=resolve);$('#account-reload').click();
    root.innerHTML='<p>Other view</p>';finish(account(false));await settle();
    assert.equal(root.textContent,'Other view');
});

test('late profile navigation cannot overwrite security content or the current membership palette',async t=>{
    const {root,$}=await setup(t,'CUSTOMER');root.id='view-container';
    const {loadCustomerView}=await import('../public/js/view-customer.js');
    let finish, requests=0;
    api.get=async path=>{
        if(path==='/customer-portal/overview'){
            if(++requests===1)return new Promise(resolve=>finish=resolve);
            return {totalLoyaltyPoints:1500,loyaltyPoints:100};
        }
        if(path==='/account/profile')return account(false);
        throw new Error('Stored membership totals should not require a history request');
    };
    const pending=loadCustomerView('profile');await loadCustomerView('security');
    finish({totalLoyaltyPoints:6000,loyaltyPoints:6000});await pending;
    assert.equal(document.body.dataset.customerTier,'gold');
    assert.equal($('.account-settings-view h1').textContent,'Bảo mật tài khoản');
    assert.equal($('#account-profile-form'),null);
});
