// Generic handhelds v2, outlines guided by real devices (wide: AYN Odin 3, vertical: Retroid Pocket Classic),
// drawn clean like the controllers: same 657-wide frame, same line style. No shoulder buttons on the body:
// those are drawn by shoulderKeys() so every device shows them the same, clearly.
function rr(x,y,w,h,rt,rb){return `M${x+rt},${y} H${x+w-rt} A${rt},${rt} 0 0 1 ${x+w},${y+rt} V${y+h-rb} A${rb},${rb} 0 0 1 ${x+w-rb},${y+h} H${x+rb} A${rb},${rb} 0 0 1 ${x},${y+h-rb} V${y+rt} A${rt},${rt} 0 0 1 ${x+rt},${y} Z`}
function rrBulge(x,y,w,h,rt,rb,b){return `M${x+rt},${y} H${x+w-rt} A${rt},${rt} 0 0 1 ${x+w},${y+rt} Q${x+w+2*b},${y+(rt+h-rb)/2} ${x+w},${y+h-rb} A${rb},${rb} 0 0 1 ${x+w-rb},${y+h} H${x+rb} A${rb},${rb} 0 0 1 ${x},${y+h-rb} Q${x-2*b},${y+(rt+h-rb)/2} ${x},${y+rt} A${rt},${rt} 0 0 1 ${x+rt},${y} Z`}
const HH={
  // Odin 3: body 732 × 285 (photo px) → ×0.8; corners ≈ 72; screen 436 × 247 centred; sticks/dpad/face at measured spots
  // traced from a front photo of the AYN Odin 3 (outline only, left half mirrored so it's symmetric)
  wide:{VW:657,VH:300,k:1,ox:63,oy:58,body:[63,58,582,237,46,56],trace:"M127.6,58.9 C126.3,59.1 124.3,59.3 122.7,59.6 C121.0,59.8 119.4,60.1 117.7,60.4 C116.1,60.8 114.5,61.2 112.9,61.6 C111.3,62.1 109.7,62.5 108.1,63.1 C106.6,63.7 105.0,64.4 103.5,65.2 C102.1,65.9 100.6,66.8 99.3,67.8 C98.0,68.8 96.7,69.9 95.5,71.1 C94.3,72.2 93.3,73.5 92.2,74.8 C91.1,76.0 90.1,77.4 89.0,78.6 C88.0,79.9 86.9,81.2 85.8,82.5 C84.8,83.8 83.7,85.0 82.6,86.3 C81.5,87.6 80.4,88.8 79.4,90.2 C78.4,91.5 77.4,92.9 76.5,94.3 C75.7,95.7 74.8,97.1 74.1,98.6 C73.3,100.1 72.7,101.6 72.0,103.2 C71.4,104.7 70.8,106.3 70.3,107.9 C69.8,109.4 69.3,111.0 68.8,112.6 C68.4,114.2 68.0,115.9 67.6,117.5 C67.2,119.1 66.9,120.7 66.5,122.4 C66.2,124.0 66.0,125.7 65.7,127.3 C65.5,128.9 65.2,130.6 65.0,132.3 C64.8,133.9 64.7,135.6 64.5,137.2 C64.3,138.9 64.2,140.5 64.1,142.2 C64.0,143.9 63.8,145.5 63.7,147.2 C63.6,148.9 63.6,150.5 63.5,152.2 C63.4,153.9 63.3,155.5 63.3,157.2 C63.2,158.8 63.2,160.5 63.1,162.2 C63.1,163.8 63.1,165.5 63.1,167.2 C63.1,168.8 63.0,170.5 63.0,172.2 C63.0,173.8 63.0,175.5 63.0,177.2 C63.0,178.8 63.0,180.5 63.0,182.2 C63.0,183.8 63.0,185.5 63.0,187.2 C63.1,188.8 63.1,190.5 63.1,192.2 C63.1,193.8 63.2,195.5 63.2,197.2 C63.3,198.8 63.3,200.5 63.4,202.2 C63.5,203.8 63.6,205.5 63.7,207.2 C63.8,208.8 63.9,210.5 64.0,212.2 C64.2,213.8 64.3,215.5 64.5,217.1 C64.7,218.8 64.9,220.4 65.1,222.1 C65.3,223.7 65.6,225.4 65.8,227.0 C66.1,228.7 66.4,230.3 66.8,232.0 C67.1,233.6 67.5,235.2 67.9,236.8 C68.4,238.4 68.8,240.0 69.3,241.6 C69.8,243.2 70.4,244.8 71.0,246.3 C71.6,247.9 72.2,249.4 72.9,250.9 C73.6,252.5 74.3,254.0 75.1,255.4 C75.9,256.9 76.8,258.3 77.7,259.7 C78.6,261.1 79.5,262.5 80.5,263.8 C81.5,265.2 82.6,266.5 83.7,267.7 C84.8,269.0 85.9,270.2 87.1,271.4 C88.3,272.6 89.5,273.7 90.7,274.8 C91.9,276.0 93.2,277.1 94.4,278.2 C95.7,279.2 96.9,280.3 98.2,281.4 C99.5,282.5 100.8,283.5 102.2,284.5 C103.5,285.5 104.9,286.4 106.4,287.2 C107.8,287.9 109.4,288.6 111.0,289.1 C112.6,289.6 114.2,289.9 115.9,290.2 C117.5,290.5 119.2,290.7 120.8,290.8 C122.5,291.0 124.1,291.1 125.8,291.2 C127.5,291.3 129.1,291.4 130.8,291.5 C132.5,291.5 134.1,291.6 135.8,291.7 C137.5,291.7 139.1,291.8 140.8,291.9 C142.4,291.9 144.1,292.0 145.8,292.0 C147.4,292.1 149.1,292.1 150.8,292.2 C152.4,292.3 154.1,292.3 155.8,292.4 C157.4,292.5 159.1,292.5 160.8,292.6 C162.4,292.6 164.1,292.7 165.8,292.7 C167.4,292.8 169.1,292.8 170.8,292.9 C172.4,292.9 174.1,292.9 175.8,292.9 C177.4,293.0 179.1,293.0 180.8,293.0 C182.4,293.0 184.1,293.0 185.8,293.0 C187.4,293.1 189.1,293.1 190.8,293.1 C192.4,293.1 194.1,293.2 195.8,293.2 C197.4,293.2 199.1,293.2 200.8,293.3 C202.4,293.3 204.1,293.3 205.8,293.3 C207.4,293.3 209.1,293.3 210.8,293.3 C212.4,293.3 214.1,293.3 215.8,293.3 C217.4,293.3 219.1,293.2 220.8,293.2 C222.4,293.2 224.1,293.2 225.8,293.1 C227.4,293.1 229.1,293.1 230.8,293.1 C232.4,293.1 234.1,293.0 235.8,293.0 C237.4,293.0 239.1,293.0 240.8,293.0 C242.4,293.0 244.1,293.0 245.8,293.0 C247.4,293.0 249.1,293.0 250.8,293.0 C252.4,293.0 254.1,293.0 255.8,293.0 C257.4,293.0 259.1,293.0 260.8,293.0 C262.4,293.0 264.1,293.0 265.8,293.0 C267.4,293.0 269.1,293.0 270.8,293.0 C272.4,293.0 274.1,293.0 275.8,293.0 C277.4,293.0 279.1,293.0 280.8,293.0 C282.4,293.0 284.1,293.0 285.8,293.0 C287.4,293.0 289.1,293.0 290.8,293.0 C292.4,293.0 294.1,293.0 295.8,293.0 C297.4,293.0 299.1,293.0 300.8,293.0 C302.4,293.0 304.1,293.0 305.8,293.0 C307.4,293.0 309.1,293.0 310.8,293.0 C312.4,293.0 314.1,293.0 315.8,293.0 C317.4,293.0 319.1,293.0 320.8,293.0 C322.4,293.0 324.1,293.0 325.8,293.0 C327.4,293.0 329.1,293.0 330.8,293.0 C332.4,293.0 334.1,293.0 335.8,293.0 C337.4,293.0 339.1,293.0 340.8,293.0 C342.4,293.0 344.1,293.0 345.8,293.0 C347.4,293.0 349.1,293.0 350.8,293.0 C352.4,293.0 354.1,293.0 355.8,293.0 C357.4,293.0 359.1,293.0 360.8,293.0 C362.4,293.0 364.1,293.0 365.8,293.0 C367.4,293.0 369.1,293.0 370.8,293.0 C372.4,293.0 374.1,293.0 375.8,293.0 C377.4,293.0 379.1,293.0 380.8,293.0 C382.4,293.0 384.1,293.0 385.8,293.0 C387.4,293.0 389.1,293.0 390.8,293.0 C392.4,293.0 394.1,293.0 395.8,293.0 C397.4,293.0 399.1,293.0 400.8,293.0 C402.4,293.0 404.1,293.0 405.8,293.0 C407.4,293.0 409.1,293.0 410.8,293.0 C412.4,293.0 414.1,293.0 415.8,293.0 C417.4,293.0 419.1,293.0 420.8,293.0 C422.4,293.0 424.1,293.0 425.8,293.0 C427.4,293.0 429.1,293.0 430.8,293.0 C432.4,293.0 434.1,293.0 435.8,293.0 C437.4,293.0 439.1,293.0 440.8,293.0 C442.4,293.0 444.1,293.0 445.8,293.0 C447.4,293.0 449.1,293.0 450.8,293.0 C452.4,293.0 454.1,293.0 455.8,293.0 C457.4,293.0 459.1,293.0 460.8,293.0 C462.4,293.0 464.1,293.0 465.8,293.0 C467.4,293.0 469.1,293.0 470.8,293.0 C472.4,293.0 474.1,293.1 475.8,293.1 C477.4,293.1 479.1,293.1 480.8,293.1 C482.4,293.2 484.1,293.2 485.8,293.2 C487.4,293.2 489.1,293.3 490.8,293.3 C492.4,293.3 494.1,293.3 495.8,293.3 C497.4,293.3 499.1,293.3 500.8,293.3 C502.4,293.3 504.1,293.3 505.8,293.3 C507.4,293.2 509.1,293.2 510.8,293.2 C512.4,293.2 514.1,293.1 515.8,293.1 C517.4,293.1 519.1,293.1 520.8,293.0 C522.4,293.0 524.1,293.0 525.8,293.0 C527.4,293.0 529.1,293.0 530.8,292.9 C532.4,292.9 534.1,292.9 535.8,292.9 C537.4,292.8 539.1,292.8 540.8,292.7 C542.4,292.7 544.1,292.6 545.8,292.6 C547.4,292.5 549.1,292.5 550.7,292.4 C552.4,292.3 554.1,292.3 555.7,292.2 C557.4,292.2 559.1,292.1 560.7,292.0 C562.4,292.0 564.1,291.9 565.7,291.9 C567.4,291.8 569.1,291.7 570.7,291.7 C572.4,291.6 574.1,291.6 575.7,291.5 C577.4,291.4 579.1,291.3 580.7,291.2 C582.4,291.1 584.1,291.0 585.7,290.9 C587.4,290.7 589.0,290.6 590.7,290.3 C592.3,290.0 594.0,289.7 595.6,289.2 C597.1,288.7 598.7,288.1 600.2,287.4 C601.7,286.7 603.1,285.7 604.5,284.8 C605.8,283.8 607.1,282.8 608.4,281.7 C609.7,280.7 610.9,279.6 612.2,278.5 C613.5,277.4 614.7,276.3 615.9,275.1 C617.2,274.0 618.4,272.9 619.6,271.7 C620.7,270.5 621.9,269.3 623.0,268.1 C624.1,266.8 625.2,265.5 626.2,264.2 C627.2,262.9 628.2,261.5 629.1,260.1 C630.0,258.7 630.8,257.3 631.6,255.8 C632.4,254.4 633.2,252.9 633.9,251.4 C634.6,249.9 635.3,248.3 635.9,246.8 C636.5,245.2 637.0,243.7 637.5,242.1 C638.1,240.5 638.5,238.9 639.0,237.3 C639.4,235.7 639.8,234.1 640.1,232.4 C640.5,230.8 640.8,229.2 641.1,227.5 C641.4,225.9 641.6,224.2 641.8,222.6 C642.1,220.9 642.3,219.3 642.4,217.6 C642.6,216.0 642.8,214.3 642.9,212.6 C643.1,211.0 643.2,209.3 643.3,207.7 C643.4,206.0 643.5,204.3 643.6,202.7 C643.6,201.0 643.7,199.3 643.8,197.7 C643.8,196.0 643.8,194.3 643.9,192.7 C643.9,191.0 643.9,189.3 643.9,187.7 C644.0,186.0 644.0,184.3 644.0,182.7 C644.0,181.0 644.0,179.3 644.0,177.7 C644.0,176.0 644.0,174.3 644.0,172.7 C644.0,171.0 644.0,169.3 643.9,167.7 C643.9,166.0 643.9,164.3 643.9,162.7 C643.8,161.0 643.8,159.3 643.7,157.7 C643.7,156.0 643.6,154.3 643.5,152.7 C643.5,151.0 643.4,149.3 643.3,147.7 C643.2,146.0 643.1,144.4 643.0,142.7 C642.8,141.0 642.7,139.4 642.5,137.7 C642.4,136.0 642.2,134.4 642.0,132.7 C641.8,131.1 641.6,129.4 641.4,127.8 C641.1,126.1 640.9,124.5 640.5,122.8 C640.2,121.2 639.9,119.6 639.5,117.9 C639.2,116.3 638.7,114.7 638.3,113.1 C637.8,111.5 637.4,109.9 636.8,108.3 C636.3,106.7 635.8,105.2 635.1,103.6 C634.5,102.1 633.9,100.5 633.1,99.0 C632.4,97.6 631.6,96.1 630.7,94.7 C629.8,93.3 628.9,91.9 627.9,90.6 C626.9,89.2 625.8,87.9 624.7,86.7 C623.7,85.4 622.6,84.1 621.5,82.9 C620.4,81.6 619.3,80.3 618.3,79.0 C617.2,77.7 616.2,76.4 615.1,75.1 C614.1,73.9 613.0,72.6 611.8,71.4 C610.6,70.2 609.4,69.1 608.1,68.1 C606.8,67.1 605.3,66.2 603.9,65.4 C602.4,64.6 600.9,63.9 599.3,63.3 C597.8,62.7 596.2,62.2 594.6,61.7 C593.0,61.3 591.4,60.9 589.7,60.5 C588.1,60.2 586.5,59.9 584.8,59.6 C583.2,59.4 581.5,59.2 579.9,59.0 C578.2,58.8 576.6,58.7 574.9,58.5 C573.2,58.4 571.6,58.3 569.9,58.3 C568.2,58.2 566.6,58.2 564.9,58.1 C563.2,58.1 561.6,58.1 559.9,58.1 C558.2,58.1 556.6,58.1 554.9,58.1 C553.2,58.1 551.6,58.1 549.9,58.2 C548.2,58.2 546.6,58.3 544.9,58.4 C543.2,58.5 541.6,58.6 539.9,58.8 C538.3,58.9 536.6,59.1 535.0,59.4 C533.3,59.6 531.7,59.9 530.0,60.2 C528.4,60.5 526.7,60.8 525.1,61.0 C523.5,61.3 521.8,61.6 520.2,61.8 C518.5,62.0 516.9,62.2 515.2,62.4 C513.5,62.5 511.9,62.6 510.2,62.7 C508.5,62.8 506.9,62.8 505.2,62.9 C503.5,62.9 501.9,62.9 500.2,63.0 C498.5,63.0 496.9,63.0 495.2,63.0 C493.5,63.0 491.9,63.0 490.2,63.0 C488.5,63.0 486.9,63.0 485.2,63.0 C483.5,63.0 481.9,63.0 480.2,63.0 C478.5,63.0 476.9,63.0 475.2,63.0 C473.5,63.0 471.9,63.0 470.2,63.0 C468.5,63.0 466.9,63.0 465.2,63.0 C463.5,63.0 461.9,63.0 460.2,63.0 C458.5,63.0 456.9,63.0 455.2,63.0 C453.5,63.0 451.9,63.0 450.2,63.0 C448.5,63.0 446.9,63.0 445.2,63.0 C443.5,63.0 441.9,63.0 440.2,63.0 C438.5,63.0 436.9,63.0 435.2,63.0 C433.5,63.0 431.9,63.0 430.2,63.0 C428.5,63.0 426.9,63.0 425.2,63.0 C423.5,63.0 421.9,63.0 420.2,63.0 C418.5,63.0 416.9,63.0 415.2,63.0 C413.5,63.0 411.9,63.0 410.2,63.0 C408.5,63.0 406.9,63.0 405.2,63.0 C403.5,63.0 401.9,63.0 400.2,63.0 C398.5,63.0 396.9,63.0 395.2,63.0 C393.5,63.0 391.9,63.0 390.2,63.0 C388.5,63.0 386.9,63.0 385.2,63.0 C383.5,63.0 381.9,63.0 380.2,63.0 C378.5,63.0 376.9,63.0 375.2,63.0 C373.5,63.0 371.9,63.0 370.2,63.0 C368.5,63.0 366.9,63.0 365.2,63.0 C363.5,63.0 361.9,63.0 360.2,63.0 C358.5,63.0 356.9,63.0 355.2,63.0 C353.5,63.0 351.9,63.0 350.2,63.0 C348.5,63.0 346.9,63.0 345.2,63.0 C343.5,63.0 341.9,63.0 340.2,63.0 C338.5,63.0 336.9,63.0 335.2,63.0 C333.5,63.0 331.9,63.0 330.2,63.0 C328.5,63.0 326.9,63.0 325.2,63.0 C323.5,63.0 321.9,63.0 320.2,63.0 C318.5,63.0 316.9,63.0 315.2,63.0 C313.5,63.0 311.9,63.0 310.2,63.0 C308.5,63.0 306.9,63.0 305.2,63.0 C303.5,63.0 301.9,63.0 300.2,63.0 C298.5,63.0 296.9,63.0 295.2,63.0 C293.5,63.0 291.9,63.0 290.2,63.0 C288.5,63.0 286.9,63.0 285.2,63.0 C283.5,63.0 281.9,63.0 280.2,63.0 C278.5,63.0 276.9,63.0 275.2,63.0 C273.5,63.0 271.9,63.0 270.2,63.0 C268.5,63.0 266.9,63.0 265.2,63.0 C263.5,63.0 261.9,63.0 260.2,63.0 C258.5,63.0 256.9,63.0 255.2,63.0 C253.5,63.0 251.9,63.0 250.2,63.0 C248.5,63.0 246.9,63.0 245.2,63.0 C243.5,63.0 241.9,63.0 240.2,63.0 C238.5,63.0 236.9,63.0 235.2,63.0 C233.5,63.0 231.9,63.0 230.2,63.0 C228.5,63.0 226.9,63.0 225.2,63.0 C223.5,63.0 221.9,63.0 220.2,63.0 C218.5,63.0 216.9,63.0 215.2,63.0 C213.5,63.0 211.9,63.0 210.2,63.0 C208.5,63.0 206.9,63.0 205.2,62.9 C203.5,62.9 201.9,62.9 200.2,62.8 C198.5,62.8 196.9,62.7 195.2,62.6 C193.6,62.5 191.9,62.4 190.2,62.2 C188.6,62.1 186.9,61.8 185.3,61.6 C183.6,61.4 182.0,61.1 180.3,60.8 C178.7,60.5 177.1,60.2 175.4,59.9 C173.8,59.6 172.1,59.4 170.5,59.2 C168.8,58.9 167.2,58.8 165.5,58.6 C163.8,58.5 162.2,58.4 160.5,58.3 C158.9,58.2 157.2,58.2 155.5,58.1 C153.9,58.1 152.2,58.1 150.5,58.1 C148.9,58.1 147.2,58.1 145.5,58.1 C143.9,58.1 142.2,58.1 140.5,58.2 C138.9,58.2 137.2,58.3 135.5,58.4 C133.9,58.4 131.9,58.6 130.5,58.7 C129.2,58.8 128.9,58.8 127.6,58.9Z",screen:[182,80,345,194],
        ls:[121,149],rs:[574,233],stickR:23,dpad:[127,231,31,10.5],face:[585,149.5,28,12.9],sel:[151,86],start:[557,86],pill:[34,20],sh:[162,546]},
  // Retroid Pocket Classic: body 368 × 547 → near-square screen on top, controls below
  vertical:{VW:360,VH:500,k:0.85,ox:205,oy:20,body:[205,20,368,547,16,44],screen:[236,46,306,262],
        dpad:[305,428,47.5,15.5],face:[472,428,47.5,19.5],sel:[346,532],start:[432,532],pill:[44,20]}
};
// The vertical handheld with sticks: same body, D-pad and face buttons move up, the two sticks sit below them
// between Select and Start. Draw it with Object.assign(HH.vertical, HH_VERTICAL_STICKS); the spacing check applies too.
const HH_VERTICAL_STICKS={VH:496,body:[205,20,368,548,16,44],dpad:[305,400,47.5,15.5],face:[472,398,47.5,19.5],ls:[346,510],rs:[432,510],stickR:20,sel:[258,510],start:[520,510]};
function handheld2(kind, w, opt){
  opt=opt||{}; const G=HH[kind], on=opt.on||{}, style=opt.style||'xbox', ACC=TOKENS.brandAccent, line='#5a5e6a', detail='#33363f';
  const v=k=>on[k]||0, lit=k=>v(k)>0?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#hg2)"`:`fill="#0f1014" stroke="${line}"`;
  // map photo coordinates into the frame, centred
  const k=G.k, bw=G.body[2]*k, bh=G.body[3]*k, x0=(G.VW-bw)/2, y0=(G.VH-bh)/2;
  const X=x=>x0+(x-G.body[0])*k, Y=y=>y0+(y-G.body[1])*k;
  const bodyD=G.trace?null:G.bulge?rrBulge(x0,y0,bw,bh,G.body[4]*k,G.body[5]*k,G.bulge*k):rr(x0,y0,bw,bh,G.body[4]*k,G.body[5]*k);
  let s=G.trace?`<path transform="translate(${x0-G.body[0]},${y0-G.body[1]})" d="${G.trace}" fill="#101116" stroke="${line}" stroke-width="2.5"/>`:`<path d="${bodyD}" fill="#101116" stroke="${line}" stroke-width="2.5"/>`;
  const sc=G.screen; s+=`<rect x="${X(sc[0])}" y="${Y(sc[1])}" width="${sc[2]*k}" height="${sc[3]*k}" rx="${10}" fill="#0C0D11" stroke="${detail}" stroke-width="2"/>`;
  const stick=([x,y],r,kk)=>`<circle cx="${X(x)}" cy="${Y(y)}" r="${r+8}" fill="#0c0d11" stroke="${detail}" stroke-width="2"/><circle cx="${X(x)}" cy="${Y(y)}" r="${r}" ${lit(kk)} stroke-width="2"/>`;
  if(G.ls){ s+=stick(G.ls,G.stickR,'LS')+stick(G.rs,G.stickR,'RS'); }
  { const [dx0,dy0,L0,t0]=G.dpad; s+=dpadCross(X(dx0),Y(dy0),L0*k,t0*k,on); }
  { const [fx0,fy0,g0,r0]=G.face, fx=X(fx0), fy=Y(fy0), g=g0*k, r=r0*k*1.05;
    s+=[['Y',0,-g],['X',-g,0],['B',g,0],['A',0,g]].map(([kk,ddx,ddy])=>{const x=fx+ddx,y=fy+ddy;let gl;
      if(style==='ps'){const col=v(kk)>0.5?TOKENS.inkDark:TOKENS.face.ps[kk];gl=psGlyph(kk,x,y,r*0.47,col);}
      else{const lab=style==='switch'?{A:'B',B:'A',X:'Y',Y:'X'}[kk]:kk;const col=v(kk)>0.5?TOKENS.inkDark:style==='switch'?TOKENS.inkLight:TOKENS.face.xbox[kk];gl=`<text x="${x}" y="${y}" fill="${col}" font-family="I" font-weight="700" font-size="${r*LETTER}" text-anchor="middle" dominant-baseline="central">${lab}</text>`;}
      return `<circle cx="${x}" cy="${y}" r="${r}" ${lit(kk)} stroke-width="2"/>${gl}`;}).join(''); }
  const [pw,ph]=G.pill||[22,12]; const pill=([x,y],kk)=>`<rect x="${X(x)-pw/2}" y="${Y(y)-ph/2}" width="${pw}" height="${ph}" rx="${ph/2}" ${lit(kk)} stroke-width="2"/>`;
  s+=pill(G.sel,'SELECT')+pill(G.start,'START');
  // where the shoulders sit, for shoulderKeys(): above the top corners
  const anchors={kind, left:G.sh?X(G.sh[0]):X(G.body[0])+G.body[4]*k*0.9, right:G.sh?X(G.sh[1]):X(G.body[0]+G.body[2])-G.body[4]*k*0.9, top:y0, L:x0, R:x0+bw, rc:G.body[4]*k};
  return {svg:`<defs><filter id="hg2" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>${s}`, VW:G.VW, VH:G.VH, anchors};
}

// ---------------- Three ways to show the shoulder buttons ----------------
// A "floating keys": a trigger and a bumper key hovering above each shoulder, like web gamepad testers
// B "top view": a small strip of the device seen from above, where shoulder buttons are big
// C "fill bars": like A, but the trigger is a bar that fills up as it's pressed (how input overlays show triggers)
let _tc=0;
function shoulderKeys(variant, anchors, style, on, scale){
  scale=scale||1; const ACC=TOKENS.brandAccent, line='#5a5e6a', v=k=>on[k]||0;
  const lit=k=>v(k)>0?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#hg2)"`:`fill="#0f1014" stroke="${line}"`;
  const ink=k=>v(k)>0.5?TOKENS.inkDark:'#C9CCD3';
  const N={xbox:['LT','LB','RT','RB'],ps:['L2','L1','R2','R1'],switch:['ZL','L','ZR','R']}[style];
  const bw=96*scale, bh=30*scale, tw=72*scale, th=(variant==='C'?50:40)*scale, gap=10*scale, lift=14*scale;
  let s='';
  [['left',0],['right',1]].forEach(([side,i])=>{
    const cx=anchors[side], kT=i?'RT':'LT', kB=i?'RB':'LB', nT=N[i?2:0], nB=N[i?3:1];
    const by=anchors.top-lift-bh, ty=by-gap-th;
    s+=`<rect x="${cx-bw/2}" y="${by}" width="${bw}" height="${bh}" rx="${bh/2}" ${lit(kB)} stroke-width="2.2"/><text x="${cx}" y="${by+bh/2+1}" fill="${ink(kB)}" font-family="I" font-weight="700" font-size="${14*scale}" text-anchor="middle" dominant-baseline="central">${nB}</text>`;
    if(variant==='C'){
      const fill=Math.min(1,v(kT)); const r=12*scale, id='tc'+(_tc++);
      s+=`<rect x="${cx-tw/2}" y="${ty}" width="${tw}" height="${th}" rx="${r}" fill="#0f1014" stroke="${fill>0?ACC:line}" stroke-width="2.2" ${fill>0?'filter="url(#hg2)"':''}/>`+
         (fill>0?`<clipPath id="${id}"><rect x="${cx-tw/2}" y="${ty}" width="${tw}" height="${th}" rx="${r}"/></clipPath><rect x="${cx-tw/2}" y="${ty+th*(1-fill)}" width="${tw}" height="${th*fill}" fill="${ACC}" fill-opacity=".9" clip-path="url(#${id})"/>`:'')+
         `<text x="${cx}" y="${ty+th/2+1}" fill="#C9CCD3" font-family="I" font-weight="700" font-size="${14*scale}" text-anchor="middle" dominant-baseline="central">${nT}</text>`+
         (fill>0?`<clipPath id="${id}f"><rect x="${cx-tw/2}" y="${ty+th*(1-fill)}" width="${tw}" height="${th*fill}"/></clipPath><text x="${cx}" y="${ty+th/2+1}" fill="${TOKENS.inkDark}" clip-path="url(#${id}f)" font-family="I" font-weight="700" font-size="${14*scale}" text-anchor="middle" dominant-baseline="central">${nT}</text>`:'');
    } else {
      // a trigger keycap: rounded top, flatter bottom, like the real thing seen from the front
      const r=th*0.5, x1=cx-tw/2, x2=cx+tw/2, yb=ty+th;
      s+=`<path d="M${x1},${yb-6} V${ty+r} A${tw/2},${r} 0 0 1 ${x2},${ty+r} V${yb-6} Q${x2},${yb} ${x2-6},${yb} H${x1+6} Q${x1},${yb} ${x1},${yb-6} Z" ${lit(kT)} stroke-width="2.2"/><text x="${cx}" y="${ty+th*0.58}" fill="${ink(kT)}" font-family="I" font-weight="700" font-size="${14*scale}" text-anchor="middle" dominant-baseline="central">${nT}</text>`;
    }
  });
  return s;
}
// B: the device's top edge, seen from above, with the shoulder buttons where they really are
function topView(W, anchors, style, on, y, S){ S=S||1;
  const ACC=TOKENS.brandAccent, line='#5a5e6a', v=k=>on[k]||0, lit=k=>v(k)>0?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#hg2)"`:`fill="#0f1014" stroke="${line}"`;
  const ink=k=>v(k)>0.5?TOKENS.inkDark:'#C9CCD3', N={xbox:['LT','LB','RT','RB'],ps:['L2','L1','R2','R1'],switch:['ZL','L','ZR','R']}[style];
  const x0=anchors.left-70*S, x1=anchors.right+70*S, h=34*S;
  let s=`<rect x="${x0}" y="${y}" width="${x1-x0}" height="${h}" rx="${h/2}" fill="#101116" stroke="${line}" stroke-width="2.2"/>`;
  [[anchors.left,0],[anchors.right,1]].forEach(([cx,i])=>{const kT=i?'RT':'LT',kB=i?'RB':'LB';const dir=i?1:-1;
    s+=`<rect x="${cx-48*S}" y="${y+h-8*S}" width="${96*S}" height="${28*S}" rx="${10*S}" ${lit(kB)} stroke-width="2"/><text x="${cx}" y="${y+h+6*S}" fill="${ink(kB)}" font-family="I" font-weight="700" font-size="${14*S}" text-anchor="middle" dominant-baseline="central">${N[i?3:1]}</text>`;
    s+=`<rect x="${cx-36*S+dir*8*S}" y="${y-22*S}" width="${72*S}" height="${30*S}" rx="${11*S}" ${lit(kT)} stroke-width="2"/><text x="${cx+dir*8*S}" y="${y-7*S}" fill="${ink(kT)}" font-family="I" font-weight="700" font-size="${14*S}" text-anchor="middle" dominant-baseline="central">${N[i?2:0]}</text>`;});
  return s+`<text x="${(x0+x1)/2}" y="${y+h/2+1}" fill="#6B6E77" font-family="I" font-size="${12*S}" text-anchor="middle" dominant-baseline="central">seen from the top</text>`;
}

// Option A, refined: keys shaped for the device they float over
// Floating shoulder keys, one rule on every device (28 Oct 2026), as shares of the bumper's height:
// KEY_GAP between trigger and bumper, KEY_AIR between bumper and the body. Air > gap, so the two keys
// read as one pair that belongs to the shoulder, clearly apart from the body; both leave room for the lit rings.
const KEY_GAP=0.6, KEY_AIR=0.9;
function shoulderKeysA(anchors, style, on, scale){
  scale=scale||1; const ACC=TOKENS.brandAccent, line='#5a5e6a', v=k=>on[k]||0;
  const lit=k=>v(k)>0?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#hg2)"`:`fill="#0f1014" stroke="${line}"`;
  const ink=k=>v(k)>0.5?TOKENS.inkDark:'#C9CCD3';
  const N={xbox:['LT','LB','RT','RB'],ps:['L2','L1','R2','R1'],switch:['ZL','L','ZR','R']}[style];
  const txt=(x,y,t,k,fs)=>`<text x="${x}" y="${y}" fill="${ink(k)}" font-family="I" font-weight="700" font-size="${fs*scale}" text-anchor="middle" dominant-baseline="central">${t}</text>`;
  let s='';
  if(anchors.kind==='vertical'){
    // Vertical: both keys hug the top corners, inside the body's width, like a Game Boy-style shoulder
    const W=Math.min(110*scale,(anchors.R-anchors.L)/2-14), bh=32*scale, th=36*scale, gap=KEY_GAP*bh, lift=KEY_AIR*bh, inset=0, rc=Math.max(anchors.rc+4*scale,20*scale);
    [0,1].forEach(i=>{const kB=i?'RB':'LB', kT=i?'RT':'LT', dir=i?-1:1;
      const xo=i?anchors.R-inset:anchors.L+inset;              // outer edge, flush inside the body side
      const xi=xo+dir*W;                                         // inner end
      const corner=(y,h,w)=>{ // outer corner rounded like the body, inner end a half-circle
        const x2=xo+dir*w, r=h/2, sw=i?0:1;
        return `M${xo},${y+h} V${y+Math.min(rc,h)} A${Math.min(rc,h)},${Math.min(rc,h)} 0 0 ${sw} ${xo+dir*Math.min(rc,h)},${y} H${x2-dir*r} A${r},${r} 0 0 ${sw} ${x2-dir*r},${y+h} Z`;};
      const by=anchors.top-lift-bh, ty=by-gap-th, tw=W*0.78;
      s+=`<path d="${corner(by,bh,W)}" ${lit(kB)} stroke-width="2.2" stroke-linejoin="round"/>`+txt((xo+xi)/2,by+bh/2+1,N[i?3:1],kB,16);
      s+=`<path d="${corner(ty,th,tw)}" ${lit(kT)} stroke-width="2.2" stroke-linejoin="round"/>`+txt(xo+dir*tw/2,ty+th/2+1,N[i?2:0],kT,16);
    });
    return s;
  }
  // Wide handheld and controller: bumper = pill, trigger = taller rounded rectangle on top
  const bw=(anchors.kind==='ctrl'?96:112)*scale, bh=30*scale, tw=80*scale, th=40*scale, gap=KEY_GAP*bh, lift=KEY_AIR*bh;
  [0,1].forEach(i=>{const cx=i?anchors.right:anchors.left, kT=i?'RT':'LT', kB=i?'RB':'LB';
    const by=anchors.top-lift-bh, ty=by-gap-th;
    s+=`<rect x="${cx-bw/2}" y="${by}" width="${bw}" height="${bh}" rx="${bh/2}" ${lit(kB)} stroke-width="2.2"/>`+txt(cx,by+bh/2+1,N[i?3:1],kB,16);
    s+=`<rect x="${cx-tw/2}" y="${ty}" width="${tw}" height="${th}" rx="${14*scale}" ${lit(kT)} stroke-width="2.2"/>`+txt(cx,ty+th/2+1,N[i?2:0],kT,16);
  });
  return s;
}

// Spacing guard: every control keeps at least MIN_GAP (drawing units) from every other control,
// the screen and the body edge. Runs on every render; a crowded layout is reported, never shipped.
const MIN_GAP=12;
function spacingCheck(kind){
  const G=HH[kind], k=G.k, out=[], sh=[];
  const C=(n,x,y,r)=>sh.push({n,t:'c',x:x*k,y:y*k,r:r*k}), R=(n,x0,y0,x1,y1)=>sh.push({n,t:'r',x0:x0*k,y0:y0*k,x1:x1*k,y1:y1*k});
  const [sx,sy,sw,shh]=G.screen; R('screen',sx,sy,sx+sw,sy+shh);
  if(G.ls){const rr=(G.stickR+8)/k; C('LS',...G.ls,rr); C('RS',...G.rs,rr);}
  const [dx,dy,L,t]=G.dpad; R('dpad-v',dx-t,dy-L,dx+t,dy+L); R('dpad-h',dx-L,dy-t,dx+L,dy+t);
  const [fx,fy,g,r0]=G.face, fr=r0*1.05; [['Y',0,-g],['X',-g,0],['B',g,0],['A',0,g]].forEach(([n,a,b])=>C(n,fx+a,fy+b,fr));
  const [pw,ph]=(G.pill||[22,12]).map(v=>v/k); [['SELECT',G.sel],['START',G.start]].forEach(([n,[x,y]])=>R(n,x-pw/2,y-ph/2,x+pw/2,y+ph/2));
  const dist=(a,b)=>{ // clearance between two shapes
    if(a.t==='c'&&b.t==='c') return Math.hypot(a.x-b.x,a.y-b.y)-a.r-b.r;
    if(a.t==='r'&&b.t==='r') return Math.max(a.x0-b.x1,b.x0-a.x1,a.y0-b.y1,b.y0-a.y1);
    const c=a.t==='c'?a:b, q=a.t==='c'?b:a, nx=Math.max(q.x0,Math.min(c.x,q.x1)), ny=Math.max(q.y0,Math.min(c.y,q.y1));
    return Math.hypot(c.x-nx,c.y-ny)-c.r; };
  // distance from a point to the inside edge of the body (rounded rectangle; top and bottom corners differ)
  const [bx,by,bw,bh,brt,brb]=G.body.map((v,i)=>i<6?v*k:v);
  const edge=(x,y)=>{const r=y<by+bh/2?brt:brb, cx=Math.min(Math.max(x,bx+r),bx+bw-r), cy=Math.min(Math.max(y,by+r),by+bh-r);
    const inCorner=(x<bx+r||x>bx+bw-r)&&(y<by+r||y>by+bh-r);
    return inCorner? r-Math.hypot(x-cx,y-cy) : Math.min(x-bx,bx+bw-x,y-by,by+bh-y);};
  sh.forEach(q=>{ if(q.n==='screen') return; let d;
    if(q.t==='c'){ d=Infinity; for(let a=0;a<64;a++){const t=a/64*2*Math.PI; d=Math.min(d,edge(q.x+q.r*Math.cos(t),q.y+q.r*Math.sin(t)));} }
    else { d=Math.min(...[[q.x0,q.y0],[q.x1,q.y0],[q.x0,q.y1],[q.x1,q.y1]].map(([x,y])=>edge(x,y))); }
    if(d<MIN_GAP) out.push(`${q.n} ↔ body edge: ${d.toFixed(1)}`); });
  const face=new Set(['Y','X','B','A']);
  for(let i=0;i<sh.length;i++)for(let j=i+1;j<sh.length;j++){const a=sh[i],b=sh[j];
    if(a.n.startsWith('dpad')&&b.n.startsWith('dpad')) continue;
    const need=face.has(a.n)&&face.has(b.n)?4*k*0+6:MIN_GAP; // face buttons sit as a tight cluster, like on a real pad
    const d=dist(a,b); if(d<need) out.push(`${a.n} ↔ ${b.n}: ${d.toFixed(1)}`);}
  return out;
}
