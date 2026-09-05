# Interaction Flow

## Event Handlers

| Element | Event | Handler |
|---------|-------|----------|
| left-tab, active | click | switchLeftTab(0) |
| btnReReadId | click | reReadIdCard() |
| btnCallNext | click | callNext() |
| btnReCall | click | reCall() |
| btnSkipNumber | click | skipNumber() |
| btnPauseService | click | pauseService() |
| btnConfirmApplicant | click | confirmApplicant() |
| btnFinishEvaluate | click | finishAndEvaluate() |
| heroSearchInput | keydown | if(event.key==='Enter')handleHeroSearch() |
| hotTabPersonal | click | switchHotTab('personal') |
| hotTabLegal | click | switchHotTab('legal') |
| hotTabDept | click | switchHotTab('dept') |
| q1Yes | click | selectQ1('yes') |
| q1No | click | selectQ1('no') |
| q1FatherEthnic | input | updateQuestionHint() |
| q1MotherEthnic | input | updateQuestionHint() |
| q2Father | click | selectQ2('father') |
| q2Mother | click | selectQ2('mother') |
| q2SettleAddr | input | updateQuestionHint() |
| q2Relation | input | updateQuestionHint() |
| q2Police | input | updateQuestionHint() |
| q3Yes | click | selectQ3('yes') |
| q3No | click | selectQ3('no') |
| q3ProxyName | input | updateQuestionHint();if(appMode==='flow'&¤tStep===0)updateRightPanelFlow() |
| q3ProxyId | input | updateQuestionHint();if(appMode==='flow'&¤tStep===0)updateRightPanelFlow() |
| q3ProxyPhone | input | updateQuestionHint() |
| matItem0 | click | selectMaterial(0) |
| matItem1 | click | selectMaterial(1) |
| matItem2 | click | selectMaterial(2) |
| matItem3 | click | selectMaterial(3) |
| matItem4 | click | selectMaterial(4) |
| babyName | input | updateProgress() |
| babyName | focus | showFormTip('babyName') |
| babyName | blur | hideFormTip() |
| birthDate | input | updateProgress() |
| birthDate | focus | showFormTip('birthDate') |
| birthDate | blur | hideFormTip() |
| birthTime | input | updateProgress() |
| birthTime | focus | showFormTip('birthTime') |
| birthTime | blur | hideFormTip() |
| birthWeight | input | updateProgress() |
| birthWeight | focus | showFormTip('birthWeight') |
| birthWeight | blur | hideFormTip() |
| birthLength | input | updateProgress() |
| birthLength | focus | showFormTip('birthLength') |
| birthLength | blur | hideFormTip() |
| birthPlace | input | updateProgress() |
| birthPlace | focus | showFormTip('birthPlace') |
| birthPlace | blur | hideFormTip() |
| birthOrder | input | updateProgress() |
| birthOrder | focus | showFormTip('birthOrder') |
| birthOrder | blur | hideFormTip() |
| motherName | input | updateProgress() |
| motherName | focus | showFormTip('motherName') |
| motherName | blur | hideFormTip() |
| motherId | input | updateProgress() |
| motherId | focus | showFormTip('motherId') |
| motherId | blur | hideFormTip() |
| motherAge | input | updateProgress() |
| motherAge | focus | showFormTip('motherAge') |
| motherAge | blur | hideFormTip() |
| motherEthnic | input | updateProgress() |
| motherEthnic | focus | showFormTip('motherEthnic') |
| motherEthnic | blur | hideFormTip() |
| fatherName | input | updateProgress() |
| fatherName | focus | showFormTip('fatherName') |
| fatherName | blur | hideFormTip() |
| fatherId | input | updateProgress() |
| fatherId | focus | showFormTip('fatherId') |
| fatherId | blur | hideFormTip() |
| fatherAge | input | updateProgress() |
| fatherAge | focus | showFormTip('fatherAge') |
| fatherAge | blur | hideFormTip() |
| fatherEthnic | input | updateProgress() |
| fatherEthnic | focus | showFormTip('fatherEthnic') |
| fatherEthnic | blur | hideFormTip() |
| modeOnline | click | switchSignMode('online') |
| modeOffline | click | switchSignMode('offline') |
| idQ1Lost | click | selectIdQ1('lost') |
| idQ1Damaged | click | selectIdQ1('damaged') |
| idQ1DamageDesc | input | updateIdQuestionHint() |
| idQ2Yes | click | selectIdQ2('yes') |
| idQ2No | click | selectIdQ2('no') |
| idQ2Address | input | updateIdQuestionHint() |
| idQ2Receiver | input | updateIdQuestionHint() |
| idQ2Phone | input | updateIdQuestionHint() |
| idQ3Yes | click | selectIdQ3('yes') |
| idQ3No | click | selectIdQ3('no') |
| idQ3ProxyName | input | updateIdQuestionHint();if(appMode==='flow'&¤tStep===0)updateIdRightPanel() |
| idQ3ProxyId | input | updateIdQuestionHint() |
| idQ3ProxyPhone | input | updateIdQuestionHint() |
| idMatItem0 | click | selectIdMaterial(0) |
| idMatItem1 | click | selectIdMaterial(1) |
| idName | input | idUpdateProgress() |
| idName | focus | showIdFormTip('name') |
| idName | blur | hideIdFormTip() |
| idGender | input | idUpdateProgress() |
| idGender | focus | showIdFormTip('gender') |
| idGender | blur | hideIdFormTip() |
| idEthnic | input | idUpdateProgress() |
| idEthnic | focus | showIdFormTip('ethnic') |
| idEthnic | blur | hideIdFormTip() |
| idBirthDate | input | idUpdateProgress() |
| idBirthDate | focus | showIdFormTip('birthDate') |
| idBirthDate | blur | hideIdFormTip() |
| idOldId | input | idUpdateProgress() |
| idOldId | focus | showIdFormTip('oldId') |
| idOldId | blur | hideIdFormTip() |
| idPhone | input | idUpdateProgress() |
| idPhone | focus | showIdFormTip('phone') |
| idPhone | blur | hideIdFormTip() |
| idAddress | input | idUpdateProgress() |
| idAddress | focus | showIdFormTip('address') |
| idAddress | blur | hideIdFormTip() |
| idPolice | input | idUpdateProgress() |
| idPolice | focus | showIdFormTip('police') |
| idPolice | blur | hideIdFormTip() |
| idZipCode | input | idUpdateProgress() |
| idZipCode | focus | showIdFormTip('zipCode') |
| idZipCode | blur | hideIdFormTip() |
| bizQ1CompanyName | focus | showBizCompanyDropdown() |
| bizQ1CompanyName | input | filterBizCompanyDropdown() |
| bizMatItem0 | click | selectBizMaterial(0) |
| bizMatItem1 | click | selectBizMaterial(1) |
| bizMatItem2 | click | selectBizMaterial(2) |
| bizMatItem3 | click | selectBizMaterial(3) |
| bizMatItem4 | click | selectBizMaterial(4) |
| bizMatItem5 | click | selectBizMaterial(5) |
| bizIsAgentNo | focus | showBizFormTip('bizIsAgent') |
| bizIsAgentNo | click | bizSelectAgent('no') |
| bizIsAgentYes | focus | showBizFormTip('bizIsAgent') |
| bizIsAgentYes | click | bizSelectAgent('yes') |
| bizAgentName | focus | showBizFormTip('bizAgentName') |
| bizAgentName | input | bizUpdateProgress() |
| bizAgentCetfType | focus | showBizFormTip('bizAgentCetfType') |
| bizAgentCetfType | change | bizUpdateProgress() |
| bizAgentCetfNo | focus | showBizFormTip('bizAgentCetfNo') |
| bizAgentCetfNo | input | bizUpdateProgress() |
| bizAgentTelephone | focus | showBizFormTip('bizAgentTelephone') |
| bizAgentTelephone | input | bizUpdateProgress() |
| bizFName | focus | showBizFormTip('bizFName') |
| bizFPersnName | focus | showBizFormTip('bizFPersnName') |
| bizFPersnCetfType | focus | showBizFormTip('bizFPersnCetfType') |
| bizFPersnCetfNo | focus | showBizFormTip('bizFPersnCetfNo') |
| bizFRepealType | change | bizRepealTypeChange() |
| bizTaxNo | click | bizSelectTax('no') |
| bizTaxYes | click | bizSelectTax('yes') |
| bizFClearTaxFileNo | input | bizUpdateProgress() |
| bizFTaxRegOrgan | input | bizUpdateProgress() |
| bizFMemo | input | bizUpdateProgress() |
| bizModeOnline | click | bizSwitchSignMode('online') |
| bizModeOffline | click | bizSwitchSignMode('offline') |
| hwQ1New | click | selectHwQ1('new') |
| hwQ1Expand | click | selectHwQ1('expand') |
| hwQ1OldProject | input | updateHwQuestionHint() |
| hwQ2Yes | click | selectHwQ2('yes') |
| hwQ2No | click | selectHwQ2('no') |
| hwQ2Regions | input | updateHwQuestionHint() |
| hwQ2Coordinator | input | updateHwQuestionHint() |
| hwQ3Yes | click | selectHwQ3('yes') |
| hwQ3No | click | selectHwQ3('no') |
| hwQ3ProxyName | input | updateHwQuestionHint();if(appMode==='flow'&¤tStep===0)updateHwRightPanel() |
| hwQ3ProxyId | input | updateHwQuestionHint();if(appMode==='flow'&¤tStep===0)updateHwRightPanel() |
| hwQ3ProxyPhone | input | updateHwQuestionHint() |
| hwMatItem0 | click | selectHwMaterial(0) |
| hwMatItem1 | click | selectHwMaterial(1) |
| hwMatItem2 | click | selectHwMaterial(2) |
| hwMatItem3 | click | selectHwMaterial(3) |
| hwMatItem4 | click | selectHwMaterial(4) |
| hwProjectName | input | updateHwProgress() |
| hwProjectName | focus | showHwFormTip('projectName') |
| hwProjectName | blur | hideHwFormTip() |
| hwProjectCode | focus | showHwFormTip('projectCode') |
| hwProjectCode | blur | hideHwFormTip() |
| hwProjectRoute | input | updateHwProgress() |
| hwProjectRoute | focus | showHwFormTip('projectRoute') |
| hwProjectRoute | blur | hideHwFormTip() |
| hwProjectLength | input | updateHwProgress() |
| hwProjectLength | focus | showHwFormTip('projectLength') |
| hwProjectLength | blur | hideHwFormTip() |
| hwRoadClass | change | updateHwProgress() |
| hwRoadClass | focus | showHwFormTip('roadClass') |
| hwRoadClass | blur | hideHwFormTip() |
| hwDesignSpeed | input | updateHwProgress() |
| hwDesignSpeed | focus | showHwFormTip('designSpeed') |
| hwDesignSpeed | blur | hideHwFormTip() |
| hwStartDate | input | updateHwProgress() |
| hwStartDate | focus | showHwFormTip('startDate') |
| hwStartDate | blur | hideHwFormTip() |
| hwDuration | input | updateHwProgress() |
| hwDuration | focus | showHwFormTip('duration') |
| hwDuration | blur | hideHwFormTip() |
| hwContractor | input | updateHwProgress() |
| hwContractor | focus | showHwFormTip('contractor') |
| hwContractor | blur | hideHwFormTip() |
| hwQualification | input | updateHwProgress() |
| hwQualification | focus | showHwFormTip('qualification') |
| hwQualification | blur | hideHwFormTip() |
| hwProjectManager | input | updateHwProgress() |
| hwProjectManager | focus | showHwFormTip('projectManager') |
| hwProjectManager | blur | hideHwFormTip() |
| hwManagerCert | input | updateHwProgress() |
| hwManagerCert | focus | showHwFormTip('managerCert') |
| hwManagerCert | blur | hideHwFormTip() |
| btnPrev | click | prevStep() |
| btnNext | click | nextStep() |
| rightTabBtn0 | click | switchRightTab(0) |
| rightTabBtn1 | click | switchRightTab(1) |
| switchSearchInput | keydown | if(event.key==='Enter')handleSwitchSearch() |
| switchTabPersonal | click | switchSwitchTab('personal') |
| switchTabLegal | click | switchSwitchTab('legal') |
| switchTabDept | click | switchSwitchTab('dept') |
| matConfirmModal | click | if(event.target===this)hideMatConfirmModal() |
| acceptNoticeModal | click | if(event.target===this)hideACCEPTNotice() |
| signModal | click | if(event.target===this)closeSignModal() |

## Functions

| Name | Parameters | Body Preview |
|------|------------|--------------|
| openSignModal | type | currentSignType = type;
  currentSignFlow = 'birth';
  signModal = document.getElementById('signModal');
  signCanvas = document.getElementById('signCanvas');
  signCtx = signCanvas.getContext('2d');
 |
| closeSignModal |  | if (signModal) signModal.style.display = 'none'; |
| startDraw | e | isDrawing = true;
  lastX = e.offsetX;
  lastY = e.offsetY;
  hasDrawn = true;
  const placeholder = document.getElementById('signPlaceholder');
  if (placeholder) placeholder.classList.add('hidden'); |
| draw | e | if (!isDrawing) return;
  signCtx.beginPath();
  signCtx.moveTo(lastX, lastY);
  signCtx.lineTo(e.offsetX, e.offsetY);
  signCtx.stroke();
  lastX = e.offsetX;
  lastY = e.offsetY; |
| endDraw |  | isDrawing = false; |
| handleTouchStart | e | e.preventDefault();
  const touch = e.touches[0];
  const rect = signCanvas.getBoundingClientRect();
  const scaleX = signCanvas.width / rect.width;
  const scaleY = signCanvas.height / rect.height;
  |
| handleTouchMove | e | e.preventDefault();
  const touch = e.touches[0];
  const rect = signCanvas.getBoundingClientRect();
  const scaleX = signCanvas.width / rect.width;
  const scaleY = signCanvas.height / rect.height;
  |
| clearSignCanvas |  | if (signCtx) {
    signCtx.clearRect(0, 0, signCanvas.width, signCanvas.height); |
| saveSign |  | if (!hasDrawn) {
    showToast('error', '请先完成签名');
    return; |
| updateSignStatus | type, signData | updateSignStatusUI(type); |
| updateSignStatusUI | person | const statusEl = document.getElementById(person === 'mother' ? 'motherStatus' : 'fatherStatus');
  const confirmEl = document.getElementById(person === 'mother' ? 'signConfirmMother' : 'signConfirmFat |
| updateSignMaterialPreview |  | // 在材料预览中添加签名标签页
  const materialTabs = document.getElementById('materialTabs');
  const signTab = document.getElementById('signMaterialTab');
  
  if (!signTab && (motherSignData || fatherSignData))  |
| switchSignMode | mode | signMode = mode;
  const onlineBtn = document.getElementById('modeOnline');
  const offlineBtn = document.getElementById('modeOffline');
  const onlineContent = document.getElementById('onlineSignCont |
| downloadMaterial | index | const materials = ['出生医学证明申领表', '父母身份信息核验表', '出生信息记录'];
  showToast('info', '正在下载：' + materials[index]);
  setTimeout(() => {
    showToast('success', materials[index] + ' 下载完成'); |
| downloadAllMaterials |  | showToast('info', '正在下载所有材料...');
  setTimeout(() => {
    showToast('success', '所有材料下载完成，请打印后签名'); |
| offlineMatScanUpload | index | const materials = ['出生医学证明申领表', '父母身份信息核验表', '出生信息记录'];
  showToast('info', '正在使用高拍仪上传：' + materials[index]);
  setTimeout(() => {
    completeOfflineMatUpload(index); |
| offlineMatQrUpload | index | const materials = ['出生医学证明申领表', '父母身份信息核验表', '出生信息记录'];
  showToast('info', '正在扫码上传：' + materials[index]);
  setTimeout(() => {
    completeOfflineMatUpload(index); |
| completeOfflineMatUpload | index | const statusEl = document.getElementById('offlineMatStatus' + index);
  const itemEl = document.getElementById('offlineMatUpload' + index);
  
  if (statusEl) {
    statusEl.textContent = '已上传';
    s |
| checkAllOfflineMaterialsUploaded |  | const allUploaded = [0, 1, 2].every(i => {
    const status = document.getElementById('offlineMatStatus' + i);
    return status && status.classList.contains('completed'); |
| updateMaterialPreviewAfterSign |  | // 检查是否所有材料都已上传完成（按材料维度检查）
  const allMatsUploaded = [0, 1, 2].every(i => {
    const status = document.getElementById('offlineMatStatus' + i);
    return status && status.classList.contains('complete |
| switchIdMaterialTab | index | document.querySelectorAll('#idMaterialTabs .material-tab').forEach((tab, i) => {
    tab.classList.toggle('active', i === index); |
| openIdSignModal |  | currentSignFlow = 'id';
  idSignModal = document.getElementById('signModal');
  idSignCanvas = document.getElementById('signCanvas');
  idSignCtx = idSignCanvas.getContext('2d');
  
  const title = do |
| idStartDraw | e | idIsDrawing = true;
  idLastX = e.offsetX;
  idLastY = e.offsetY;
  idHasDrawn = true;
  const placeholder = document.getElementById('signPlaceholder');
  if (placeholder) placeholder.classList.add('h |
| idDraw | e | if (!idIsDrawing) return;
  idSignCtx.beginPath();
  idSignCtx.moveTo(idLastX, idLastY);
  idSignCtx.lineTo(e.offsetX, e.offsetY);
  idSignCtx.stroke();
  idLastX = e.offsetX;
  idLastY = e.offsetY; |
| idEndDraw |  | idIsDrawing = false; |
| idHandleTouchStart | e | e.preventDefault();
  const touch = e.touches[0];
  const rect = idSignCanvas.getBoundingClientRect();
  const scaleX = idSignCanvas.width / rect.width;
  const scaleY = idSignCanvas.height / rect.hei |
| idHandleTouchMove | e | e.preventDefault();
  const touch = e.touches[0];
  const rect = idSignCanvas.getBoundingClientRect();
  const scaleX = idSignCanvas.width / rect.width;
  const scaleY = idSignCanvas.height / rect.hei |
| saveIdSign |  | if (!idHasDrawn) {
    showToast('error', '请先完成签名');
    return; |
| updateIdMaterialPreview |  | const fields = {
    'idField-name': 'idName',
    'idField-gender': 'idGender',
    'idField-ethnic': 'idEthnic',
    'idField-birthDate': 'idBirthDate',
    'idField-oldId': 'idOldId',
    'idField- |
| updateIdSignMaterialPreview |  | const materialTabs = document.getElementById('idMaterialTabs');
  const signTab = document.getElementById('idSignMaterialTab');
  
  if (!signTab && idSignData) {
    const newTab = document.createEle |
| updateMaterialPreview |  | const fields = {
    'field-newbornName': 'babyName',
    'field-motherName': 'motherName',
    'field-fatherName': 'fatherName',
    'field-birthDate': 'birthDate',
    'field-motherAge': 'motherAge' |
| expandGuide |  |  |
| collapseGuide |  |  |
| collapseGuideDelay |  |  |
| selectQ1 | val | q1Answer=val;
  const yes=document.getElementById('q1Yes'),no=document.getElementById('q1No'),yesDot=document.getElementById('q1YesDot'),noDot=document.getElementById('q1NoDot');
  yes.style.borderCol |
| selectQ2 | val | q2Answer=val;
  const f=document.getElementById('q2Father'),m=document.getElementById('q2Mother'),fDot=document.getElementById('q2FatherDot'),mDot=document.getElementById('q2MotherDot');
  f.style.bor |
| selectQ3 | val | q3Answer=val;
  const yes=document.getElementById('q3Yes'),no=document.getElementById('q3No'),yesDot=document.getElementById('q3YesDot'),noDot=document.getElementById('q3NoDot');
  yes.style.borderCol |
| updateQuestionHint |  | const hint=document.getElementById('questionHint');
  if(q1Answer&&q2Answer&&q3Answer){
    let ethnicDesc='';
    if(q1Answer==='yes'){ethnicDesc='一致（汉族）' |
| updateTime |  | const now=new Date();const str=now.getFullYear()+'-'+String(now.getMonth()+1).padStart(2,'0')+'-'+String(now.getDate()).padStart(2,'0')+' '+String(now.getHours()).padStart(2,'0')+':'+String(now.getMin |
| updateDuration |  | durationSeconds++;const h=String(Math.floor(durationSeconds/3600)).padStart(2,'0'),m=String(Math.floor((durationSeconds%3600)/60)).padStart(2,'0'),s=String(durationSeconds%60).padStart(2,'0');document |
| switchTab | el | document.querySelectorAll('.nav-tab').forEach(t=>t.classList.remove('active'));el.classList.add('active') |
| switchLeftTab | idx | document.querySelectorAll('.left-tab').forEach((t,i)=>{t.classList.toggle('active',i===idx) |
| switchRightTab | idx | document.querySelectorAll('.right-tab').forEach((t,i)=>{t.classList.toggle('active',i===idx) |
| switchMaterialTab | idx | document.querySelectorAll('.material-tab').forEach((t,i)=>{t.classList.toggle('active',i===idx) |
| showHomeMode |  | appMode='home';
  document.getElementById('homeHero').style.display='';
  document.getElementById('homeHotSection').style.display='';
  document.getElementById('searchResultCard').classList.remove('sh |
| showSearchMode |  | appMode='search';
  document.getElementById('homeHero').style.display='none';
  document.getElementById('homeHotSection').style.display='none';
  document.getElementById('searchResultCard').classList. |
| showFlowMode |  | appMode='flow';currentStep=0;
  document.getElementById('homeHero').style.display='none';
  document.getElementById('homeHotSection').style.display='none';
  document.getElementById('searchResultCard' |
| updateRightPanelFlow |  | const c=document.getElementById('flowRightContent');
  const tabHtml='<div style="display:flex;gap:0;background:var(--bg-page);border-radius:8px;padding:3px;margin:8px 12px"><button class="rp-tab rp-t |
| switchRpTab | btn | document.querySelectorAll('.rp-tab').forEach(t=>t.classList.remove('rp-tab-active'));
  btn.classList.add('rp-tab-active');
  const tab=btn.dataset.tab;
  const guideEl=document.getElementById('rpTabG |
| checkEligibility |  | let canHandle=true;
  let reasons=[];
  if(q1Answer===null||q2Answer===null||q3Answer===null){
    canHandle=false;
    reasons.push('请先完成智能导办问卷，系统将为您进行资格判定');
    return {canHandle:false,reasons:reas |
| renderGuideDoc |  | let materials='1. 新生儿父母双方身份证原件<br>2. 居民户口簿原件<br>3. 医疗机构签发的出生信息记录<br>4. 结婚证原件';
  if(q3Answer==='yes'){
    const proxyName=document.getElementById('q3ProxyName');
    const proxyId=document.getElement |
| renderStepFaq | type | let html='<div class="panel-section" style="margin-top:8px"><div class="section-header"><div class="section-title"><span>❓</span>常见问题</div></div><div style="padding:12px;font-size:12px;line-height:1.7 |
| showSwitchItemModal |  | const modal=document.getElementById('switchItemModal');
  if(!modal)return;
  modal.style.display='flex';
  switchModalTab='personal';
  document.getElementById('switchSearchInput').value='';
  docume |
| hideSwitchItemModal |  | const modal=document.getElementById('switchItemModal');
  if(modal)modal.style.display='none'; |
| switchSwitchTab | tab | document.querySelectorAll('.switch-hot-tab').forEach(t=>t.classList.remove('switch-hot-tab-active'));
  if(tab==='personal')document.getElementById('switchTabPersonal').classList.add('switch-hot-tab-a |
| renderSwitchHotGrid | tab | const grid=document.getElementById('switchHotGrid');if(!grid)return;
  let items=[];
  if(tab==='personal'){
    items=[
      {icon:'🪪',name:'居民身份证补领',dept:'湖南省公安厅',tag:'免申办',tagType:'free' |
| handleSwitchSearch |  | const input=document.getElementById('switchSearchInput');
  const text=input.value.trim();if(!text)return;
  const resultCard=document.getElementById('switchSearchResultCard');
  const hotGrid=documen |
| switchSelectItem | name | hideSwitchItemModal();
  confirmItem(name); |
| confirmItem | name | if(name==='居民身份证补领'){
    startIdCardFlow();
    return; |
| switchHotTab | tab | document.querySelectorAll('.hot-tab').forEach(t=>t.classList.remove('hot-tab-active'));
  if(tab==='personal')document.getElementById('hotTabPersonal').classList.add('hot-tab-active');
  else if(tab== |
| handleHeroSearch |  | const input=document.getElementById('heroSearchInput');
  const text=input.value.trim();if(!text)return;
  input.value='';
  showSearchMode(); |
| heroQuickSearch | text | document.getElementById('heroSearchInput').value=text;
  showSearchMode(); |
| renderHotGrid | tab | const grid=document.getElementById('hotGridHero');if(!grid)return;
  let items=[];
  if(tab==='personal'){
    items=[
      {icon:'🪪',name:'居民身份证补领',dept:'湖南省公安厅',tag:'免申办',tagType:'free' |
| updateFlowBar |  | const isHw=currentFlowItem==='公路建设项目施工许可';
  const isId=currentFlowItem==='居民身份证补领';
  const isBiz=currentFlowItem==='个体工商户注销登记';
  let auditFlag;
  if(isHw){auditFlag=hwAuditShown |
| goToStep | step | if(step>currentStep+1||appMode!=='flow')return;
  const isHw=currentFlowItem==='公路建设项目施工许可';
  const isId=currentFlowItem==='居民身份证补领';
  const isBiz=currentFlowItem==='个体工商户注销登记';
  currentStep=step;
 |
| nextStep |  | if(appMode!=='flow')return;
  const isHw=currentFlowItem==='公路建设项目施工许可';
  const isId=currentFlowItem==='居民身份证补领';
  const isBiz=currentFlowItem==='个体工商户注销登记';
  if(currentStep===0){
    if(isBiz){
   |
| showAuditResult |  | const isHw=currentFlowItem==='公路建设项目施工许可';
  if(isHw){
    showToast('success','信息填报完成，系统已生成审核结果'); |
| prevStep |  | if(currentStep>0&&appMode==='flow'){
    currentStep--;
    const isHw=currentFlowItem==='公路建设项目施工许可';
    const isId=currentFlowItem==='居民身份证补领';
    const isBiz=currentFlowItem==='个体工商户注销登记';
    fo |
| startFaceScan |  | const avatar=document.getElementById('faceAvatar'),badge=document.getElementById('verifyBadge');avatar.classList.add('scanning');badge.className='verify-badge pending';badge.innerHTML='<span>⏳</span>< |
| fillSuggestion | text | document.getElementById('heroSearchInput').value=text;showSearchMode() |
| handleSmartInput |  | const input=document.getElementById('heroSearchInput');const text=input.value.trim();if(!text)return;
  input.value='';
  showSearchMode(); |
| toggleVoice |  | const btn=document.getElementById('voiceBtn');isListening=!isListening;if(isListening){btn.classList.add('listening');setTimeout(()=>{btn.classList.remove('listening');isListening=false;fillSuggestion |
| selectGender | el | el.parentElement.querySelectorAll('.tab-item').forEach(t=>t.classList.remove('active'));el.classList.add('active');updateProgress() |
| autoFillForm |  | const fields=[{id:'babyName',value:'张小宇',delay:200 |
| selectMaterial | idx | selectedMatIdx=idx;
  document.querySelectorAll('.material-item').forEach((el,i)=>{
    el.style.background=i===idx?'var(--primary-bg)':'';
    el.style.borderColor=i===idx?'var(--primary)':''; |
| renderStepFaqForMaterial | idx | const faqMap=[
    [{q:'登记表去哪里领取？',a:'可在政务服务中心户籍民政窗口现场领取，也可在政务服务网下载打印。' |
| showFormTip | fieldId | selectedMatIdx=-1;
  document.querySelectorAll('.material-item').forEach(el=>{el.style.background='';el.style.borderColor='' |
| getFormFieldCategory | fieldId | if(['babyName','birthDate','birthTime','birthWeight','birthLength','birthPlace','birthOrder'].includes(fieldId))return 'newborn';
  if(['motherName','motherId','motherAge','motherEthnic','motherInfo'] |
| hideFormTip |  | const tipArea=document.getElementById('flowTipArea');
  if(!tipArea||selectedMatIdx>=0)return;
  tipArea.innerHTML='<div style="padding:12px;font-size:12px;line-height:1.7;color:var(--text-secondary)" |
| updateProgress |  | const fields=['babyName','birthDate','birthTime','birthWeight','birthLength','birthPlace','motherName','motherId','motherAge','fatherName','fatherId','fatherAge'];let filled=0;fields.forEach(id=>{cons |
| simulateUpload |  | scannerUpload() |
| updateMaterialCount |  | document.getElementById('materialCount').textContent='已共享 '+sharedCount+' · 已上传 '+uploadedCount+' · 待上传 '+pendingCount; |
| scannerUpload |  | showToast('info','📷 请选择高拍仪扫描的压缩包文件');
  setTimeout(()=>{
    const scenarios=[
      {
        type:'success',
        files:[
          {name:'出生医学记录_某医院.pdf',match:3 |
| processZipUpload | scenario, areaId, filesId, emptyHintId, matMap, totalItems | const area=document.getElementById(areaId);
  const filesContainer=document.getElementById(filesId);
  const emptyHint=document.getElementById(emptyHintId);
  
  if(scenario.type==='error'){
    showT |
| retryZipUpload | btn | const fileDiv=btn.closest('div[style*="error-bg"],div[style*="error-bg"]');
  if(fileDiv){
    fileDiv.style.opacity='0';
    fileDiv.style.transform='translateX(20px)';
    fileDiv.style.transition=' |
| qrCodeUpload |  | showToast('info','📱 请使用手机扫描二维码上传文件');
  setTimeout(()=>{
    showToast('success','手机已连接，等待上传...');
    setTimeout(()=>{
      const fileName='结婚证照片_手机上传.jpg';
      addRecognizingFile(fileName,'phone' |
| addRecognizingFile | fileName, source | const area=document.getElementById('recognizingArea');
  const container=document.getElementById('recognizingFiles');
  const emptyHint=document.getElementById('uploadEmptyHint');
  area.style.display |
| simulateRecognition | fileId, fileName | let progress=30;
  const statusEl=document.getElementById(fileId+'_status');
  const progressEl=document.getElementById(fileId+'_progress');
  const interval=setInterval(()=>{
    progress+=Math.rando |
| matchMaterial | fileName | for(let i=0;i<materialMap.length;i++){
    const m=materialMap[i];
    for(let j=0;j<m.keywords.length;j++){
      if(fileName.includes(m.keywords[j]))return m; |
| moveFileToMaterial | fileId, matched | const fileDiv=document.getElementById(fileId);
  const matItem=document.getElementById(matched.id);
  if(!matItem||!fileDiv)return;
  const check=matItem.querySelector('.material-check');
  const stat |
| deleteThumb | thumbId, matItemId | const thumb=document.getElementById(thumbId);
  if(!thumb)return;
  thumb.style.opacity='0';thumb.style.transform='scale(0.8)';thumb.style.transition='all .2s';
  setTimeout(()=>{
    const thumbRow=t |
| manualClassify | fileId | const fileDiv=document.getElementById(fileId);
  if(!fileDiv)return;
  const sel=document.createElement('select');
  sel.className='form-control';
  sel.style.cssText='width:auto;font-size:12px;paddin |
| toggleFaq | el | el.classList.toggle('open') |
| showGuideModal |  | const overlay=document.createElement('div');
  overlay.className='mat-modal-overlay';
  overlay.onclick=function(e){if(e.target===overlay)overlay.remove() |
| showFaqModal |  | const overlay=document.createElement('div');
  overlay.className='mat-modal-overlay';
  overlay.onclick=function(e){if(e.target===overlay)overlay.remove() |
| focusAuditItem | tabIdx, fieldId | switchMaterialTab(tabIdx);
  document.querySelectorAll('.audit-result-item').forEach(i=>i.classList.remove('active'));
  const clickedItem=event?event.currentTarget:null;
  if(clickedItem)clickedItem. |
| renderFaqs | faqs | const list=document.getElementById('faqList');
  if(!list)return;
  list.innerHTML='';
  faqs.forEach(f=>{
    const div=document.createElement('div');
    div.className='faq-item';
    div.onclick=fu |
| updateFaqForItem | itemName | if(itemName==='居民身份证补领'){
    const faqArea=document.getElementById('rpFaqArea');
    if(faqArea)faqArea.innerHTML=renderIdStepFaq('guide');
    return; |
| resetFaqToGlobal |  | renderFaqs(globalFaqs); |
| showMatDetail |  | var flow,idx,type;
  if(arguments.length===3){flow=arguments[0];idx=arguments[1];type=arguments[2] |
| showToast | type, message | const c=document.getElementById('toastContainer');const icons={success:'✅',warning:'⚠️',info:'💡',error:'❌' |
| createParticles |  | const c=document.getElementById('particlesContainer');const colors=['#165DFF','#00B42A','#FF7D00','#F53F3F','#4080FF','#23C343'];for(let i=0;i<30;i++){const p=document.createElement('div');p.className |
| saveDraft |  | showToast('success','暂存成功');resetAll() |
| selectIdQ1 | val | idQ1Answer=val;
  ['idQ1Lost','idQ1Damaged'].forEach(id=>{
    const el=document.getElementById(id);if(el)el.style.borderColor=''; |
| selectIdQ2 | val | idQ2Answer=val;
  ['idQ2Yes','idQ2No'].forEach(id=>{
    const el=document.getElementById(id);if(el)el.style.borderColor=''; |
| selectIdQ3 | val | idQ3Answer=val;
  ['idQ3Yes','idQ3No'].forEach(id=>{
    const el=document.getElementById(id);if(el)el.style.borderColor=''; |
| updateIdQuestionHint |  | const hint=document.getElementById('idQuestionHint');
  if(!hint)return;
  let answers=0;
  if(idQ1Answer)answers++;
  if(idQ2Answer)answers++;
  if(idQ3Answer)answers++;
  if(answers===0){hint.innerH |
| selectIdMaterial | idx | idSelectedMatIdx=idx;
  for(let i=0;i<2;i++){
    const el=document.getElementById('idMatItem'+i);
    if(el){
      if(i===idx){el.classList.add('selected');el.style.background='var(--primary-bg)';el |
| renderIdStepFaqForMaterial | idx | const faqMap=[
    [{q:'户口簿找不到怎么办？',a:'可到户籍所在地派出所申请补发或出具户籍证明。' |
| showIdFormTip | fieldId | idSelectedMatIdx=-1;
  for(let i=0;i<2;i++){
    const el=document.getElementById('idMatItem'+i);
    if(el){el.classList.remove('selected');el.style.background='';el.style.borderLeftColor='transparen |
| hideIdFormTip |  | const tipArea=document.getElementById('flowTipArea');
  if(!tipArea||idSelectedMatIdx>=0)return;
  tipArea.innerHTML='<div style="padding:12px;font-size:12px;line-height:1.7;color:var(--text-secondary |
| showIdMatDetail | idx, type | const data=idMatDetailData[idx];if(!data)return;
  const title=data.name;
  let body='';
  if(type==='req'){
    body='<div style="margin-bottom:12px;font-size:13px;font-weight:600;color:var(--text-pr |
| idScannerUpload |  | showToast('info','📷 请选择高拍仪扫描的压缩包文件');
  setTimeout(()=>{
    const scenarios=[
      {
        type:'success',
        files:[
          {name:'居民身份证申领登记表_张明华.pdf',match:1 |
| processIdZipUpload | scenario | const area=document.getElementById('idRecognizingArea');
  const filesContainer=document.getElementById('idRecognizingFiles');
  const emptyHint=document.getElementById('idUploadEmptyHint');
  
  if(s |
| retryIdZipUpload | btn | const fileDiv=btn.parentElement;
  if(fileDiv){
    fileDiv.style.opacity='0';fileDiv.style.transform='translateX(20px)';fileDiv.style.transition='all .3s';
    setTimeout(()=>{
      fileDiv.remove() |
| checkIdRecognizingEmpty |  | const remaining=document.getElementById('idRecognizingFiles');
  if(remaining&&remaining.children.length===0){
    const recArea=document.getElementById('idRecognizingArea');
    if(recArea)recArea.st |
| addIdRecognizingFile | fileName, source | const filesContainer=document.getElementById('idRecognizingFiles');
  const sourceIcon=source==='scanner'?'📷':'📱';
  const fileId='idRecFile_'+Date.now();
  const fileDiv=document.createElement('div') |
| updateIdMaterialCount |  | const items=[document.getElementById('idMatItem0'),document.getElementById('idMatItem1')];
  let sharedCount=0,uploadedCount=0;
  items.forEach(item=>{
    if(!item)return;
    const status=item.query |
| idQrCodeUpload |  | showToast('info','请使用手机扫描屏幕二维码上传材料') |
| idAutoFillForm |  | const fields=['idName','idGender','idEthnic','idBirthDate','idOldId','idPhone','idAddress','idPolice'];
  const sampleValues={idName:'张明华',idGender:'男',idEthnic:'汉族',idBirthDate:'1994-03-15',idOldId:' |
| idUpdateProgress |  | const fields=['idName','idGender','idEthnic','idBirthDate','idOldId','idPhone','idAddress','idPolice'];
  let filled=0;
  fields.forEach(id=>{const el=document.getElementById(id);if(el&&el.value)fille |
| startIdCardFlow |  | currentFlowItem='居民身份证补领';
  idQ1Answer=null;idQ2Answer=null;idQ3Answer=null;
  idAuditShown=false;
  currentStep=0;
  totalSteps=5;
  appMode='flow';
  document.getElementById('homeHero').style.displ |
| updateIdFlowBar |  | const auditFlag=idAuditShown;
  const flowDotCount=4;
  for(let i=0;i<flowDotCount;i++){
    const dot=document.getElementById('flowDot'+i);if(!dot)continue;
    const item=dot.parentElement;
    dot. |
| updateIdRightPanel |  | const c=document.getElementById('flowRightContent');
  const tabHtml='<div style="display:flex;gap:0;background:var(--bg-page);border-radius:8px;padding:3px;margin:8px 12px"><button class="rp-tab rp-t |
| renderIdGuideDoc |  | let reason='身份证遗失补领';
  if(idQ1Answer==='damaged'){reason='身份证损坏换领' |
| renderIdStepFaq | type | let html='<div class="panel-section" style="margin-top:0"><div class="section-header"><div class="section-title"><span>❓</span>常见问题</div></div><div style="padding:12px;font-size:12px;line-height:1.6;c |
| startBizFlow |  | currentFlowItem='个体工商户注销登记';
  bizQ1Answer=null;bizQ2Answer=null;bizQ2SubAnswer=null;bizQ3Answer=null;bizAuditShown=false;bizSignDone=false;bizFilledCount=0;bizVerifyPassed=false;bizVerifyDone=false;b |
| selectBizQ1 | val | bizQ1Answer=val;
  ['bizQ1Self','bizQ1Proxy'].forEach(id=>{const el=document.getElementById(id);if(el){el.style.borderColor='transparent';el.style.background='var(--bg-page)';const dot=el.querySelecto |
| selectBizQ2 | val | bizQ2Answer=val;
  ['bizQ2_01','bizQ2_04','bizQ2_05','bizQ2_06'].forEach(id=>{const el=document.getElementById(id);if(el){el.style.borderColor='transparent';el.style.background='var(--bg-page)';const  |
| selectBizQ2Sub | val | bizQ2SubAnswer=val;
  ['bizQ2_041','bizQ2_042','bizQ2_043'].forEach(id=>{const el=document.getElementById(id);if(el){el.style.borderColor='transparent';const dot=el.querySelector('span');if(dot){dot.s |
| selectBizQ3 | val | bizQ3Answer=val;
  ['bizQ3Yes','bizQ3No'].forEach(id=>{const el=document.getElementById(id);if(el){el.style.borderColor='transparent';el.style.background='var(--bg-page)';const dot=el.querySelector('s |
| positionBizDropdown |  | var input=document.getElementById('bizQ1CompanyName');
  var dd=document.getElementById('bizCompanyDropdown');
  if(!input||!dd)return;
  var rect=input.getBoundingClientRect();
  dd.style.left=rect.l |
| showBizCompanyDropdown |  | var dd=document.getElementById('bizCompanyDropdown');
  var input=document.getElementById('bizQ1CompanyName');
  positionBizDropdown();
  filterBizCompanyDropdown();
  dd.style.display='block'; |
| filterBizCompanyDropdown |  | var input=document.getElementById('bizQ1CompanyName');
  var dd=document.getElementById('bizCompanyDropdown');
  var keyword=(input.value||'').trim().toLowerCase();
  var filtered=bizCompanyData.filte |
| selectBizCompany | name, code | document.getElementById('bizQ1CompanyName').value=name;
  document.getElementById('bizQ1CreditCode').value=code;
  document.getElementById('bizCompanyDropdown').style.display='none';
  onBizGuideInput |
| onBizGuideInput |  | const code=document.getElementById('bizQ1CreditCode')?.value?.trim()||'';
  const name=document.getElementById('bizQ1CompanyName')?.value?.trim()||'';
  if(code.length>=18 && name.length>=2){
    bizV |
| bizVerifyEnterprise | code, name | bizVerifyDone=true;bizVerifyLoading=true;bizVerifyResultHtml='';
  updateBizNextBtn();updateBizRightPanel();
  setTimeout(function(){
    bizVerifyLoading=false;
    const lastChar=code.charAt(code.le |
| updateBizNextBtn |  | const btnNext=document.getElementById('btnNext');
  if(!btnNext)return;
  const code=document.getElementById('bizQ1CreditCode')?.value?.trim()||'';
  const name=document.getElementById('bizQ1CompanyNa |
| selectBizMaterial | idx | document.querySelectorAll('[id^="bizMatItem"]').forEach((el,i)=>{el.style.background=i===idx?'var(--primary-bg)':'';el.style.borderLeft=i===idx?'3px solid var(--primary)':'none' |
| bizAutoFill |  | showToast('info','✨ 正在获取个体登记信息...');
  const data={bizFName:'南京市建邺区建国百货商店',bizFPersnName:'王建国',bizFPersnCetfType:'中华人民共和国居民身份证',bizFPersnCetfNo:'320106199001011234',bizUniScid:'92320105MA1XXXXX2R',biz |
| bizUpdateProgress |  | const total=15;let filled=0;
  const isAgent=document.getElementById('bizIsAgentYes')?.classList.contains('active');
  if(isAgent){
    if(document.getElementById('bizAgentName')?.value)filled++;
     |
| bizSelectAgent | val | const yesEl=document.getElementById('bizIsAgentYes');const noEl=document.getElementById('bizIsAgentNo');
  const fields=document.getElementById('bizAgentFields');
  const yesDot=document.getElementByI |
| bizSelectTax | val | const yesEl=document.getElementById('bizTaxYes');const noEl=document.getElementById('bizTaxNo');
  const taxRow=document.getElementById('bizTaxInfoRow');
  const yesDot=document.getElementById('bizTax |
| bizRepealTypeChange |  | const val=document.getElementById('bizFRepealType')?.value;
  const hidden1=document.getElementById('bizRepealTypeId1');const hidden2=document.getElementById('bizOtherText');
  if(val==='01'){if(hidde |
| showBizFormTip | fieldId | var tipArea=document.getElementById('flowTipArea');
  if(!tipArea)return;
  var data=bizFormTipData[fieldId];
  if(!data){tipArea.innerHTML='<div style="padding:12px;font-size:12px;color:var(--text-te |
| bizSwitchSignMode | mode | bizSignMode=mode;
  document.getElementById('bizModeOnline').classList.toggle('active',mode==='online');
  document.getElementById('bizModeOffline').classList.toggle('active',mode==='offline');
  docu |
| bizOpenSignModal |  | var modal=document.getElementById('bizSignModal');
  modal.style.display='flex';
  bizSignCanvas=document.getElementById('bizSignCanvas');
  bizSignCtx=bizSignCanvas.getContext('2d');
  bizSignCtx.cle |
| bizCloseSignModal |  | document.getElementById('bizSignModal').style.display='none';
  if(bizSignCanvas){
    bizSignCanvas.removeEventListener('mousedown',bizStartDraw);
    bizSignCanvas.removeEventListener('mousemove',bi |
| bizStartDraw | e | bizIsDrawing=true;bizSignCtx.beginPath();bizSignCtx.moveTo(e.offsetX,e.offsetY); |
| bizDraw | e | if(!bizIsDrawing)return;bizSignCtx.lineTo(e.offsetX,e.offsetY);bizSignCtx.stroke(); |
| bizEndDraw |  | bizIsDrawing=false; |
| bizHandleTouchStart | e | e.preventDefault();var t=e.touches[0];var r=bizSignCanvas.getBoundingClientRect();bizIsDrawing=true;bizSignCtx.beginPath();bizSignCtx.moveTo(t.clientX-r.left,t.clientY-r.top); |
| bizHandleTouchMove | e | e.preventDefault();if(!bizIsDrawing)return;var t=e.touches[0];var r=bizSignCanvas.getBoundingClientRect();bizSignCtx.lineTo(t.clientX-r.left,t.clientY-r.top);bizSignCtx.stroke(); |
| bizClearSign |  | if(bizSignCtx)bizSignCtx.clearRect(0,0,bizSignCanvas.width,bizSignCanvas.height); |
| bizSaveSign |  | bizCloseSignModal();
  var el=document.getElementById('bizSignConfirmOperator');
  var badge=document.getElementById('bizOperatorStatus');
  badge.textContent='已签名';badge.className='sign-status-badge  |
| bizDownloadMaterial | index | showToast('success','📥 材料下载中...'); |
| bizDownloadAllMaterials |  | showToast('success','📥 全部材料下载中...'); |
| bizOfflineMatScanUpload | index | showToast('info','📷 高拍仪扫描中...');
  setTimeout(function(){bizCompleteOfflineMatUpload(index); |
| bizOfflineMatQrUpload | index | showToast('info','📱 请扫描二维码上传');
  setTimeout(function(){bizCompleteOfflineMatUpload(index); |
| bizCompleteOfflineMatUpload | index | bizOfflineMatUploaded[index]=true;
  var statusEl=document.getElementById('bizOfflineMatStatus'+index);
  statusEl.textContent='已上传';statusEl.className='sign-status-badge success';
  var item=document |
| updateBizRightPanel |  | const panel=document.getElementById('rightPanelFlow');
  if(!panel)return;
  const tabHtml='<div style="display:flex;gap:0;background:var(--bg-page);border-radius:8px;padding:3px;margin:8px 12px"><but |
| resetAll |  | showHomeMode();resetFaqToGlobal();durationSeconds=0;q1Answer=null;q2Answer=null;q3Answer=null;hwQ1Answer=null;hwQ2Answer=null;hwQ3Answer=null;idQ1Answer=null;idQ2Answer=null;idQ3Answer=null;idAuditSho |
| selectHwQ1 | val | hwQ1Answer=val;
  ['hwQ1New','hwQ1Expand'].forEach(id=>{
    const el=document.getElementById(id);if(el)el.style.borderColor=''; |
| selectHwQ2 | val | hwQ2Answer=val;
  ['hwQ2Yes','hwQ2No'].forEach(id=>{
    const el=document.getElementById(id);if(el)el.style.borderColor=''; |
| selectHwQ3 | val | hwQ3Answer=val;
  const yes=document.getElementById('hwQ3Yes'),no=document.getElementById('hwQ3No'),yesDot=document.getElementById('hwQ3YesDot'),noDot=document.getElementById('hwQ3NoDot');
  yes.style |
| updateHwQuestionHint |  | const hint=document.getElementById('hwQuestionHint');
  if(hwQ1Answer&&hwQ2Answer&&hwQ3Answer){
    const projectType=hwQ1Answer==='new'?'新建公路项目':'改扩建公路项目';
    const crossRegion=hwQ2Answer==='yes'?'涉 |
| selectHwMaterial | idx | hwSelectedMatIdx=idx;
  document.querySelectorAll('.material-item').forEach((el,i)=>{
    if(!el.closest('#hwStep1'))return;
    el.style.background=i===idx?'var(--primary-bg)':'';
    el.style.border |
| renderHwStepFaqForMaterial | idx | const faqMap=[
    [{q:'申请书去哪里领取？',a:'可在政务服务网下载，或在交通运输窗口现场领取。' |
| showHwFormTip | fieldId | hwSelectedMatIdx=-1;
  document.querySelectorAll('.material-item').forEach(el=>{
    if(!el.closest('#hwStep1'))return;
    el.style.background='';el.style.borderColor=''; |
| hideHwFormTip |  | const tipArea=document.getElementById('flowTipArea');
  if(!tipArea||hwSelectedMatIdx>=0)return;
  tipArea.innerHTML='<div style="padding:12px;font-size:12px;line-height:1.7;color:var(--text-secondary |
| renderHwStepFaq | type | let html='<div class="panel-section" style="margin-top:8px"><div class="section-header"><div class="section-title"><span>❓</span>常见问题</div></div><div style="padding:12px;font-size:12px;line-height:1.7 |
| updateHwProgress |  | const fields=['hwProjectName','hwProjectRoute','hwProjectLength','hwRoadClass','hwDesignSpeed','hwStartDate','hwDuration','hwContractor','hwQualification','hwProjectManager'];
  let filled=0;
  fields |
| switchHwMaterialTab | idx | document.querySelectorAll('#hwMaterialTabs .material-tab').forEach((t,i)=>{t.classList.toggle('active',i===idx) |
| hwScannerUpload |  | showToast('info','📷 请选择高拍仪扫描的压缩包文件');
  setTimeout(()=>{
    const scenarios=[
      {
        type:'success',
        files:[
          {name:'施工图设计文件批复_省交通厅.pdf',match:0 |
| processHwZipUpload | scenario | const area=document.getElementById('hwRecognizingArea');
  const filesContainer=document.getElementById('hwRecognizingFiles');
  const emptyHint=document.getElementById('hwUploadEmptyHint');
  
  if(s |
| retryHwZipUpload | btn | const fileDiv=btn.parentElement;
  if(fileDiv){
    fileDiv.style.opacity='0';fileDiv.style.transform='translateX(20px)';fileDiv.style.transition='all .3s';
    setTimeout(()=>{
      fileDiv.remove() |
| checkHwRecognizingEmpty |  | const remaining=document.getElementById('hwRecognizingFiles');
  if(remaining&&remaining.children.length===0){
    const recArea=document.getElementById('hwRecognizingArea');
    if(recArea)recArea.st |
| hwQrCodeUpload |  | showToast('info','📱 请使用手机扫描二维码上传文件');
  setTimeout(()=>{
    showToast('success','手机已连接，等待上传...');
    setTimeout(()=>{
      const fileName='安全施工措施资料_手机上传.jpg';
      addHwRecognizingFile(fileName,'p |
| addHwRecognizingFile | fileName, source | const area=document.getElementById('hwRecognizingArea');
  const container=document.getElementById('hwRecognizingFiles');
  const emptyHint=document.getElementById('hwUploadEmptyHint');
  area.style.d |
| simulateHwRecognition | fileId, fileName | let progress=30;
  const statusEl=document.getElementById(fileId+'_status');
  const progressEl=document.getElementById(fileId+'_progress');
  const interval=setInterval(()=>{
    progress+=Math.rando |
| hwMatchMaterial | fileName | const map=[
    {name:'施工图设计文件批复',keywords:['施工图','设计文件','批复'] |
| updateHwMaterialCount |  | const el=document.getElementById('hwMaterialCount');
  if(el)el.textContent='已共享 '+hwSharedCount+' · 已上传 '+hwUploadedCount+' · 待上传 '+hwPendingCount; |
| showHwMatDetail | idx, type | const d=hwMatDetailData[idx];if(!d)return;
  showToast('info',type==='req'?'查看材料要求':'查看材料示例');
  selectHwMaterial(idx); |
| checkHwEligibility |  | let canHandle=true;
  let reasons=[];
  if(hwQ1Answer===null||hwQ2Answer===null||hwQ3Answer===null){
    canHandle=false;
    reasons.push('请先完成智能导办问卷，系统将为您进行资格判定');
    return {canHandle:false,reason |
| renderHwGuideDoc |  | let materials='1. 公路建设项目施工许可申请书（3份）<br>2. 企业营业执照副本<br>3. 施工图设计文件批复<br>4. 工程质量监督手续材料<br>5. 安全施工措施资料';
  if(hwQ3Answer==='yes'){
    const proxyName=document.getElementById('hwQ3ProxyName');
    const p |
| updateHwRightPanel |  | const c=document.getElementById('flowRightContent');
  const tabHtml='<div style="display:flex;gap:0;background:var(--bg-page);border-radius:8px;padding:3px;margin:8px 12px"><button class="rp-tab rp-t |
| formatDate | dateStr | const d=new Date(dateStr.replace(' ','T'));
  const now=new Date();
  const today=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  const yesterday=new Date(today.getTime()-86400000);
  cons |
| updateHistoryList | p | const list=document.getElementById('historyList');
  if(!list)return;
  list.innerHTML='';
  if(!p.history)return;
  p.history.forEach(h=>{
    const div=document.createElement('div');
    div.classNa |
| updateHotItems | p |  |
| updateRecentCases | p | const area=document.getElementById('recentCasesArea');
  const list=document.getElementById('recentCasesList');
  if(!area||!list)return;
  if(p.recentCases&&p.recentCases.length>0){
    area.style.di |
| updateSharedMaterials | p | if(!p.sharedMaterials)return;
  document.querySelectorAll('.material-item').forEach(el=>{el.style.background='';el.style.borderColor='' |
| reReadIdCard |  | const btn=document.getElementById('btnReReadId');
  if(btn)btn.disabled=true;
  
  if(currentApplicantType==='legal'){
    showToast('info','正在读取法定代表人身份证，请稍候...'); |
| handleReadFail | type | const btn = document.getElementById('btnReReadId');
  const badge = document.getElementById('verifyBadge');
  const face = document.getElementById('faceAvatar');
  
  if (face) {
    face.classList.ad |
| showReaderErrorModal | type | let title = '';
  let content = '';
  let actions = '';
  
  if (type === 'no_reader') {
    title = '🔌 读卡器未连接';
    content = `
      <div class="error-modal-content">
        <div class="error-icon" |
| closeErrorModal |  | const modal = document.getElementById('readerErrorModal');
  if (modal) modal.remove(); |
| retryReadId |  | closeErrorModal();
  reReadIdCard(); |
| manualInput |  | closeErrorModal();
  showSupplementModal(); |
| initWaitingQueue |  | waitingQueue = [
    { num: 'A101', name: '张*华', type: '个人', business: '出生医学证明' |
| updateWaitingCount |  | const countEl = document.getElementById('waitingCount');
  if (countEl) countEl.textContent = waitingQueue.length;
  renderWaitingQueue(); |
| toggleWaitingQueue |  | const list = document.getElementById('waitingQueueList');
  const toggle = document.getElementById('waitingToggle');
  
  if (list) {
    const isHidden = list.style.display === 'none';
    list.style |
| renderWaitingQueue |  | const list = document.getElementById('waitingQueueList');
  if (!list) return;
  
  if (waitingQueue.length === 0) {
    list.innerHTML = '<div class="sc-queue-empty">暂无等待人员</div>';
    return; |
| specialCall | index | if (index >= waitingQueue.length) return;
  
  const item = waitingQueue.splice(index, 1)[0];
  currentCallNum = item;
  callHistory.push(item);
  
  const numEl = document.getElementById('currentCall |
| callNext |  | if (waitingQueue.length === 0) {
    showToast('info', '暂无等待人员');
    return; |
| reCall |  | if (!currentCallNum) {
    showToast('error', '当前无叫号记录');
    return; |
| skipNumber |  | if (waitingQueue.length === 0) {
    showToast('info', '暂无等待人员');
    return; |
| pauseService |  | isPaused = !isPaused;
  const statusEl = document.getElementById('softCallStatus');
  const btnEl = document.getElementById('btnPauseService');
  
  if (isPaused) {
    if (statusEl) {
      statusEl. |
| confirmApplicant |  | if (!currentCallNum) {
    showToast('error', '当前无叫号记录');
    return; |
| finishAndEvaluate |  | if (!currentCallNum) {
    showToast('error', '当前无叫号记录');
    return; |
| enableCallButtons |  | const btns = ['btnReCall', 'btnConfirmApplicant', 'btnFinishEvaluate'];
  btns.forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.disabled = false; |
| showEvaluateModal |  | showToast('info', '评价功能开发中...'); |
| resetSoftCall |  | isPaused = false;
  currentCallNum = null;
  callHistory = [];
  
  const numEl = document.getElementById('currentCallNumber');
  if (numEl) numEl.textContent = '--';
  
  const statusEl = document.ge |
| switchApplicantType | type | currentApplicantType=type;
  document.getElementById('typePersonal').classList.toggle('active',type==='personal');
  document.getElementById('typeLegal').classList.toggle('active',type==='legal');
  c |
| showCompanySelectModal |  | selectedCompanyIdx=-1;
  let cardsHtml='';
  idPersonCompanies.forEach((c,i)=>{
    cardsHtml+='<div class="company-card" id="companyCard'+i+'" onclick="selectCompanyCard('+i+')">'+
      '<div class= |
| selectCompanyCard | idx | selectedCompanyIdx=idx;
  document.querySelectorAll('.company-card').forEach((el,i)=>{
    el.classList.toggle('selected',i===idx); |
| closeCompanySelectModal |  | const overlay=document.getElementById('companySelectOverlay');
  if(overlay)overlay.remove();
  if(selectedCompanyIdx<0){
    showToast('warning','未选择企业，请重新读取法定代表人身份证');
    const face=document.getEle |
| confirmCompanySelect |  | if(selectedCompanyIdx<0)return;
  closeCompanySelectModal();
  const company=idPersonCompanies[selectedCompanyIdx];
  
  document.getElementById('tagsSection').style.display='';
  document.querySelect |
| switchCategoryTab | idx | currentCatTab=idx;
  for(let i=0;i<3;i++){
    const btn=document.getElementById('catTab'+i);
    if(btn)btn.classList.toggle('active',i===idx); |
| renderCategoryContent | idx | const container=document.getElementById('categoryContent');
  if(!container)return;
  const data=categoryData[idx];
  if(!data)return;
  let html='';
  data.groups.forEach(g=>{
    html+='<div style=" |
| showEmptyState |  | const identityCard=document.getElementById('identityCard');
  const faceAvatar=document.getElementById('faceAvatar');
  const verifyBadge=document.getElementById('verifyBadge');
  const infoGrid=docum |
| showNormalState |  | const identityCard=document.getElementById('identityCard');
  const faceAvatar=document.getElementById('faceAvatar');
  const verifyBadge=document.getElementById('verifyBadge');
  const infoSection=do |
| showSupplementModal |  | const modal=document.getElementById('supplementModal');
  if(!modal)return;
  modal.style.display='flex';
  
  const infoGrid=document.getElementById('infoGrid');
  if(infoGrid){
    const cells=infoG |
| hideSupplementModal |  | const modal=document.getElementById('supplementModal');
  if(modal)modal.style.display='none'; |
| saveSupplement |  | const phone=document.getElementById('suppPhone').value.trim();
  const jzAddr=document.getElementById('suppJzAddr').value.trim();
  
  if(!phone){
    showToast('warning','请输入联系电话');
    return; |
| showMatConfirmModal | flowKey, matIndex | const key = flowKey + '_' + matIndex;
  currentConfirmKey = key;
  const data = matConfirmData[key];
  if (!data) return;

  const modal = document.getElementById('matConfirmModal');
  const titleEl = |
| hideMatConfirmModal |  | const modal = document.getElementById('matConfirmModal');
  if (modal) modal.style.display = 'none'; |
| confirmMatInfo |  | const key = currentConfirmKey;
  hideMatConfirmModal();
  
  // 根据材料类型，将信息带入信息填报页面
  if (key === 'biz_1') {
    // 经营者身份证 -> 带入经营者信息
    document.getElementById('bizFPersnName').value = '王建国';
    doc |
| updateMatUploadStatus | flow, index, fileName | const key = flow + '_' + index;
  const prefix = flow === 'biz' ? 'bizMatItem' : 'matItem';
  const matItem = document.getElementById(prefix + index);
  if (!matItem) return;
  const check = matItem.q |
| matScannerUpload | flow, index | const key = flow + '_' + index;
  showToast('info', '\u6b63\u5728\u901a\u8fc7\u9ad8\u62cd\u4eea\u4e0a\u4f20\u6750\u6599...');
  setTimeout(function() {
    updateMatUploadStatus(flow, index, '\u626b\u |
| matQrUpload | flow, index | const key = flow + '_' + index;
  showToast('info', '\u8bf7\u4f7f\u7528\u624b\u673a\u626b\u63cf\u5c4f\u5e55\u4e8c\u7ef4\u7801\u4e0a\u4f20\u6750\u6599');
  setTimeout(function() {
    updateMatUploadSt |
| showACCEPTNotice |  | const modal = document.getElementById('acceptNoticeModal');
  const body = document.getElementById('acceptNoticeBody');
  
  // 根据当前流程获取信息
  const isHw = currentFlowItem === '公路建设项目施工许可';
  const isId |
| hideACCEPTNotice |  | const modal = document.getElementById('acceptNoticeModal');
  if (modal) modal.style.display = 'none'; |
| printACCEPTNotice |  | window.print(); |
